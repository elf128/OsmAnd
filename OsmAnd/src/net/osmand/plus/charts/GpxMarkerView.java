package net.osmand.plus.charts;

import static android.text.format.DateUtils.SECOND_IN_MILLIS;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.mikephil.charting.charts.BarLineChartBase;
import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.data.ChartData;
import com.github.mikephil.charting.data.DataSet;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.utils.MPPointF;

import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.helpers.AndroidUiHelper;
import net.osmand.plus.utils.AndroidUtils;
import net.osmand.plus.utils.OsmAndFormatter;
import net.osmand.plus.utils.FormattedValue;
import net.osmand.util.MapUtils;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

@SuppressLint("ViewConstructor")
public class GpxMarkerView extends MarkerView {

	// Layout rows
	private final LinearLayout bubbleRow0;
	private final LinearLayout bubbleRow1;
	private final View rowDivider;

	// Item views (ordered: 0=icon, 1=elevation, 2=slope, 3=distance, 4=gain/drop)
	private final View iconContainer;
	private final View iconDivider;
	private final View firstYAxisContainer;
	private final View secondYAxisContainer;
	private final View xAxisContainer;
	private final View segmentDiffsContainer;

	// Inner dividers that may need to be toggled per arrangement
	private final View secondContainerDivider;
	private final View xAxisDivider;
	private final View segmentDiffsDivider;

	// Top and bottom bubble containers (independent horizontal translation for collision avoidance)
	private final View xAxisTopContainer;
	private final View xAxisBottomContainer;
	private final Map<GPXHighlight, float[]> bubbleOffsets = new IdentityHashMap<>();
	private final Map<GPXHighlight, int[]> bubbleWidths = new IdentityHashMap<>();
	private final TextView xAxisBottomValue;
	private final TextView xAxisBottomUnit;

	// Segment diffs text views
	private final TextView segmentGainText;
	private final TextView segmentDropText;

	// Construction-time config
	private final boolean hasIcon;
	private final long startTimeMillis;
	private final boolean useHours;
	private final boolean showXAxisValue; // true for TrackDetailsMenu time-inline

	// Feature flags (setters provided for ElevationProfileWidget)
	private boolean showElevation = true;
	private boolean showGainDrop = true;
	private boolean showDistance = false;
	private boolean twoLineMode = false;
	private boolean distanceAtBottom = false;

	// Chart height for bottom-container positioning (read from dimen on setDistanceAtBottom)
	private int chartHeightForBottom = 0;

	private final Drawable locationIcon;
	private Drawable waypointIcon;
	private Drawable destinationIcon;
	private final ImageView iconImageView;

	public GpxMarkerView(@NonNull Context context, @Nullable Drawable icon) {
		this(context, icon, 0, false, false);
	}

	public GpxMarkerView(@NonNull Context context, long startTimeMillis, boolean useHours) {
		this(context, null, startTimeMillis, useHours, true);
	}

	private GpxMarkerView(@NonNull Context context,
	                      @Nullable Drawable icon,
	                      long startTimeMillis,
	                      boolean useHours,
	                      boolean showXAxisValue) {
		super(context, R.layout.chart_marker_view);
		this.startTimeMillis = startTimeMillis;
		this.useHours = useHours;
		this.showXAxisValue = showXAxisValue;

		setClipChildren(false);
		((ViewGroup) getChildAt(0)).setClipChildren(false);

		xAxisTopContainer = findViewById(R.id.x_axis_top_container);
		bubbleRow0 = findViewById(R.id.bubble_row0);
		bubbleRow1 = findViewById(R.id.bubble_row1);
		rowDivider = findViewById(R.id.row_divider);

		iconContainer = findViewById(R.id.icon_container);
		iconDivider = findViewById(R.id.icon_divider);
		firstYAxisContainer = findViewById(R.id.first_container);
		secondYAxisContainer = findViewById(R.id.second_container);
		xAxisContainer = findViewById(R.id.x_axis_container);
		segmentDiffsContainer = findViewById(R.id.segment_diffs_container);

		secondContainerDivider = findViewById(R.id.second_container_divider);
		xAxisDivider = findViewById(R.id.x_axis_divider);
		segmentDiffsDivider = findViewById(R.id.segment_diffs_divider);

		xAxisBottomContainer = findViewById(R.id.x_axis_bottom_container);
		xAxisBottomValue = findViewById(R.id.x_axis_bottom_value);
		xAxisBottomUnit = findViewById(R.id.x_axis_bottom_unit);

		segmentGainText = findViewById(R.id.segment_gain_text);
		segmentDropText = findViewById(R.id.segment_drop_text);

		hasIcon = icon != null;
		locationIcon = icon;
		iconImageView = findViewById(R.id.icon);
		iconImageView.setImageDrawable(icon);
	}

	// --- Feature flag setters ---

	public void setShowElevation(boolean show) {
		showElevation = show;
	}

	public void setShowGainDrop(boolean show) {
		showGainDrop = show;
	}

	public void setShowDistance(boolean show) {
		showDistance = show;
	}

	public void setTwoLineMode(boolean twoLine) {
		twoLineMode = twoLine;
	}

	public void setDistanceAtBottom(boolean bottom) {
		distanceAtBottom = bottom;
		if (bottom && chartHeightForBottom == 0) {
			chartHeightForBottom = (int) getContext().getResources()
					.getDimension(R.dimen.elevation_widget_height);
		}
		FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) xAxisTopContainer.getLayoutParams();
		lp.gravity = bottom
				? (Gravity.CENTER_HORIZONTAL | Gravity.TOP)
				: (Gravity.TOP | Gravity.START);
		xAxisTopContainer.setLayoutParams(lp);
	}

	public void setWaypointIcon(@Nullable Drawable icon) {
		waypointIcon = icon;
	}

	public void setDestinationIcon(@Nullable Drawable icon) {
		destinationIcon = icon;
	}

	// --- Bubble offset state (collision avoidance) ---

	public void setOffset(@NonNull GPXHighlight highlight, float topDx, float bottomDx) {
		float[] entry = bubbleOffsets.get(highlight);
		if (entry == null) {
			entry = new float[2];
			bubbleOffsets.put(highlight, entry);
		}
		entry[0] = topDx;
		entry[1] = bottomDx;
	}

	public void clearOffsets() {
		bubbleOffsets.clear();
		bubbleWidths.clear();
		xAxisTopContainer.setTranslationX(0);
		xAxisBottomContainer.setTranslationX(0);
	}

	@NonNull
	public RectF getBubbleRect(boolean isTop, float lineX, float offsetDx, @Nullable GPXHighlight highlight) {
		int width = 0;
		if (highlight != null) {
			int[] stored = bubbleWidths.get(highlight);
			if (stored != null) {
				width = isTop ? stored[0] : stored[1];
			}
		}
		if (width == 0) {
			if (!isTop && xAxisBottomContainer.getVisibility() != VISIBLE) {
				return new RectF();
			}
			width = (isTop ? xAxisTopContainer : xAxisBottomContainer).getMeasuredWidth();
		}
		if (width == 0) {
			return new RectF();
		}
		float left = lineX - width / 2f + offsetDx;
		float right = left + width;
		if (isTop) {
			int contentTop = getContentTopPx();
			int bubbleHeight = getBubbleHeight();
			if (bubbleHeight == 0) return new RectF();
			return new RectF(left, contentTop - bubbleHeight, right, contentTop);
		} else {
			int contentBottom = getContentBottomPx();
			int labelHeight = xAxisBottomContainer.getMeasuredHeight();
			if (labelHeight == 0) return new RectF();
			return new RectF(left, contentBottom, right, contentBottom + labelHeight);
		}
	}

	// --- refreshContent ---

	@Override
	public void refreshContent(@NonNull Entry entry, @NonNull Highlight highlight) {
		ChartData<?> chartData = getChartView().getData();

		GPXHighlight gpxHighlight = highlight instanceof GPXHighlight ? (GPXHighlight) highlight : null;
		boolean showIcon = hasIcon && gpxHighlight != null
				&& (gpxHighlight.shouldShowLocationIcon() || gpxHighlight.isWaypoint());

		// Compute effective dataset count (exclude extrema dataset)
		int dataSetCount = chartData.getDataSetCount();
		if (dataSetCount > 0) {
			OrderedLineDataSet last = (OrderedLineDataSet) chartData.getDataSetByIndex(dataSetCount - 1);
			if (last.getDataSetType() == GPXDataSetType.ALTITUDE_EXTRM) {
				dataSetCount--;
			}
		}

		OrderedLineDataSet firstDataSet = null;
		OrderedLineDataSet secondDataSet = null;
		if (dataSetCount == 1 || dataSetCount == 2) {
			firstDataSet = (OrderedLineDataSet) chartData.getDataSetByIndex(0);
			secondDataSet = dataSetCount == 2
					? (OrderedLineDataSet) chartData.getDataSetByIndex(1) : null;
			if (dataSetCount == 2 && !firstDataSet.isLeftAxis()) {
				OrderedLineDataSet temp = firstDataSet;
				firstDataSet = secondDataSet;
				secondDataSet = temp;
			}
		}

		// Update y-axis content (visibility managed by arrangeItems below)
		updateYAxisContent(entry, firstDataSet, firstYAxisContainer);
		updateYAxisContent(entry, secondDataSet, secondYAxisContainer);

		// Read per-highlight data
		double currentGainM = gpxHighlight != null ? gpxHighlight.getGainM() : Double.NaN;
		double currentDropM = gpxHighlight != null ? gpxHighlight.getDropM() : Double.NaN;
		boolean currentGpsUnavail = gpxHighlight != null && gpxHighlight.isUnavailable();
		Boolean currentIsAhead = gpxHighlight != null ? gpxHighlight.getIsAhead() : null;
		boolean hasDiffsData = !Double.isNaN(currentGainM) || currentGpsUnavail;

		// Update x-axis content; use distanceM from highlight when set (tap/waypoint relative dist)
		if (firstDataSet != null) {
			double distOverride = gpxHighlight != null ? gpxHighlight.getDistanceM() : Double.NaN;
			updateXAxisContent(firstDataSet, entry, distOverride, currentIsAhead);
		}

		if (hasDiffsData) {
			OsmandApplication app = getMyApplication();
			String sign = currentIsAhead != null ? (currentIsAhead ? "+" : "-") : " ";
			segmentGainText.setText(currentGpsUnavail ? "↑ -" : "↑" + sign + OsmAndFormatter.getFormattedAlt(currentGainM, app));
			segmentDropText.setText(currentGpsUnavail ? "↓ -" : "↓" + sign + OsmAndFormatter.getFormattedAlt(currentDropM, app));
		}

		// Bottom distance container — visibility only; height is handled by onMeasure()
		if (distanceAtBottom) {
			boolean isDistType = firstDataSet != null
					&& firstDataSet.getDataSetAxisType() == GPXDataSetAxisType.DISTANCE;
			xAxisBottomContainer.setVisibility(showDistance && isDistType ? VISIBLE : GONE);
		}

		// Switch icon drawable based on highlight type
		if (showIcon) {
			Drawable icon;
			if (gpxHighlight.isDestination() && destinationIcon != null) {
				icon = destinationIcon;
			} else if (gpxHighlight.isWaypoint() && waypointIcon != null) {
				icon = waypointIcon;
			} else {
				icon = locationIcon;
			}
			iconImageView.setImageDrawable(icon);
		}

		// Arrange items into rows
		if (twoLineMode) {
			arrangeTwoLines(showIcon, dataSetCount, firstDataSet, hasDiffsData);
		} else {
			arrangeSingleLine(showIcon, dataSetCount, firstDataSet, hasDiffsData);
		}

		// Cache accurate per-highlight bubble widths for collision detection
		if (gpxHighlight != null) {
			int spec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
			xAxisTopContainer.measure(spec, spec);
			xAxisBottomContainer.measure(spec, spec);
			int[] w = bubbleWidths.get(gpxHighlight);
			if (w == null) {
				w = new int[2];
				bubbleWidths.put(gpxHighlight, w);
			}
			w[0] = xAxisTopContainer.getMeasuredWidth();
			w[1] = xAxisBottomContainer.getMeasuredWidth();
		}

		// Apply independent horizontal offsets for collision avoidance
		float[] offsets = gpxHighlight != null ? bubbleOffsets.get(gpxHighlight) : null;
		xAxisTopContainer.setTranslationX(offsets != null ? offsets[0] : 0f);
		xAxisBottomContainer.setTranslationX(offsets != null ? offsets[1] : 0f);

		super.refreshContent(entry, highlight);
	}

	// --- Content update helpers (no visibility management) ---

	private void updateYAxisContent(@NonNull Entry entry, @Nullable OrderedLineDataSet dataSet,
	                                @NonNull View container) {
		if (dataSet == null) {
			return;
		}
		TextView textValue = container.findViewById(R.id.text_value);
		TextView textUnits = container.findViewById(R.id.text_units);
		float y = getOrInterpolateY(dataSet, entry);
		String formattedValue = dataSet.getMarkerValueFormatter().formatValue(getMyApplication(), y);
		textValue.setText(formattedValue);
		textValue.setTextColor(dataSet.getColor());
		textUnits.setText(dataSet.getUnits());
	}

	@SuppressLint("SetTextI18n")
	private void updateXAxisContent(@NonNull OrderedLineDataSet dataSet, @NonNull Entry entry,
	                                double overrideDistM, @Nullable Boolean isAhead) {
		GPXDataSetAxisType xAxisType = dataSet.getDataSetAxisType();
		if (xAxisType == GPXDataSetAxisType.DISTANCE) {
			float meters = !Double.isNaN(overrideDistM)
					? (float) overrideDistM
					: entry.getX() * dataSet.getDivX();
			String sign = isAhead != null ? (isAhead ? "+" : "-") : "";
			FormattedValue fv = OsmAndFormatter.getFormattedDistanceValue(meters, getMyApplication());
			String valueStr = sign + fv.value + " ";
			((TextView) xAxisContainer.findViewById(R.id.x_axis_value)).setText(valueStr);
			TextView inlineUnit = xAxisContainer.findViewById(R.id.x_axis_unit);
			inlineUnit.setText(fv.unit);
			inlineUnit.setVisibility(VISIBLE);
			xAxisBottomValue.setText(valueStr);
			xAxisBottomUnit.setText(fv.unit);
		} else {
			if (xAxisType == GPXDataSetAxisType.TIME) {
				int seconds = (int) (entry.getX() + 0.5);
				((TextView) xAxisContainer.findViewById(R.id.x_axis_value))
						.setText(ChartUtils.formatXAxisTime(seconds, useHours));
			} else if (xAxisType == GPXDataSetAxisType.TIME_OF_DAY) {
				long seconds = (long) entry.getX();
				long timeMs = startTimeMillis + seconds * SECOND_IN_MILLIS;
				((TextView) xAxisContainer.findViewById(R.id.x_axis_value))
						.setText(OsmAndFormatter.getFormattedFullTime(timeMs));
			}
			xAxisContainer.findViewById(R.id.x_axis_unit).setVisibility(GONE);
		}
	}

	// --- Layout arrangement ---

	/**
	 * Single-line mode: rebuilds bubble_row0 from scratch.
	 * Works correctly whether called after twoLineMode or from a fresh state.
	 */
	private void arrangeSingleLine(boolean showIcon, int dataSetCount,
	                               @Nullable OrderedLineDataSet firstDataSet, boolean hasDiffsData) {
		bubbleRow0.removeAllViews();
		bubbleRow1.removeAllViews();
		rowDivider.setVisibility(GONE);
		bubbleRow1.setVisibility(GONE);
		iconDivider.setVisibility(GONE);
		boolean showFirst = showElevation && firstDataSet != null;
		boolean showSecond = showElevation && dataSetCount >= 2;
		boolean showInlineDist = !distanceAtBottom && (showXAxisValue || showDistance) && firstDataSet != null;
		boolean showDiffs = showGainDrop && hasDiffsData;

		if (showIcon) {
			iconContainer.setVisibility(VISIBLE);
			bubbleRow0.addView(iconContainer);
			if (showFirst || showSecond || showInlineDist || showDiffs) {
				iconDivider.setVisibility(VISIBLE);
				bubbleRow0.addView(iconDivider);
			}
		} else {
			iconContainer.setVisibility(GONE);
		}

		// Track whether the next non-icon item is the first in the row (no inner divider)
		boolean firstNonIcon = true;

		if (showFirst) {
			firstYAxisContainer.setVisibility(VISIBLE);
			bubbleRow0.addView(firstYAxisContainer);
			firstNonIcon = false;
		} else {
			firstYAxisContainer.setVisibility(GONE);
		}

		if (showSecond) {
			secondContainerDivider.setVisibility(!firstNonIcon ? VISIBLE : GONE);
			secondYAxisContainer.setVisibility(VISIBLE);
			bubbleRow0.addView(secondYAxisContainer);
			firstNonIcon = false;
		} else {
			secondYAxisContainer.setVisibility(GONE);
		}

		if (showInlineDist) {
			xAxisDivider.setVisibility(!firstNonIcon ? VISIBLE : GONE);
			xAxisContainer.setVisibility(VISIBLE);
			bubbleRow0.addView(xAxisContainer);
			firstNonIcon = false;
		} else {
			xAxisContainer.setVisibility(GONE);
		}

		if (showDiffs) {
			segmentDiffsDivider.setVisibility(!firstNonIcon ? VISIBLE : GONE);
			segmentDiffsContainer.setVisibility(VISIBLE);
			bubbleRow0.addView(segmentDiffsContainer);
		} else {
			segmentDiffsContainer.setVisibility(GONE);
		}
	}

	/**
	 * Two-line mode: distributes non-icon items across two rows.
	 * Icon always stays in row0; split is based on non-icon item count only.
	 */
	private void arrangeTwoLines(boolean showIcon, int dataSetCount,
	                             @Nullable OrderedLineDataSet firstDataSet, boolean hasDiffsData) {
		bubbleRow0.removeAllViews();
		bubbleRow1.removeAllViews();
		iconDivider.setVisibility(GONE);

		boolean hasIconItem = showIcon;
		List<View> items = new ArrayList<>();

		if (showElevation && firstDataSet != null) items.add(firstYAxisContainer);
		if (showElevation && dataSetCount >= 2) items.add(secondYAxisContainer);
		boolean hasInlineDist = !distanceAtBottom && (showXAxisValue || showDistance) && firstDataSet != null;
		if (hasInlineDist) items.add(xAxisContainer);
		if (showGainDrop && hasDiffsData) items.add(segmentDiffsContainer);

		if (hasIconItem) {
			iconContainer.setVisibility(VISIBLE);
			bubbleRow0.addView(iconContainer);
			if (!items.isEmpty()) {
				iconDivider.setVisibility(VISIBLE);
				bubbleRow0.addView(iconDivider);
			}
		} else {
			iconContainer.setVisibility(GONE);
		}

		int n = items.size();
		if (n == 0) {
			rowDivider.setVisibility(GONE);
			bubbleRow1.setVisibility(GONE);
			return;
		}

		// Split: ceil(n/2) items in row0, remainder in row1
		int splitAt = n > 1 ? (n + 1) / 2 : n;

		for (int i = 0; i < n; i++) {
			View item = items.get(i);
			LinearLayout targetRow = i < splitAt ? bubbleRow0 : bubbleRow1;
			boolean isFirstInRow = (i == 0) || (i == splitAt);
			item.setVisibility(VISIBLE);
			setInnerDividerVisible(item, !isFirstInRow);
			targetRow.addView(item);
		}

		boolean row1Visible = bubbleRow1.getChildCount() > 0;
		rowDivider.setVisibility(row1Visible ? VISIBLE : GONE);
		bubbleRow1.setVisibility(row1Visible ? VISIBLE : GONE);
	}

	private void setInnerDividerVisible(@NonNull View item, boolean visible) {
		View divider = null;
		if (item == secondYAxisContainer) {
			divider = secondContainerDivider;
		} else if (item == xAxisContainer) {
			divider = xAxisDivider;
		} else if (item == segmentDiffsContainer) {
			divider = segmentDiffsDivider;
		}
		if (divider != null) {
			divider.setVisibility(visible ? VISIBLE : GONE);
		}
	}

	// --- Interpolation ---

	private float getOrInterpolateY(@NonNull OrderedLineDataSet dataSet, @NonNull Entry entry) {
		if (dataSet.getEntryIndex(entry) == -1) {
			Entry upEntry = dataSet.getEntryForXValue(entry.getX(), Float.NaN, DataSet.Rounding.UP);
			Entry downEntry = upEntry;
			int upIndex = dataSet.getEntryIndex(upEntry);
			if (upIndex > 0) {
				downEntry = dataSet.getEntryForIndex(upIndex - 1);
			}
			if (downEntry != null && upEntry != null) {
				return MapUtils.getInterpolatedY(
						downEntry.getX(), downEntry.getY(),
						upEntry.getX(), upEntry.getY(),
						entry.getX());
			}
		}
		return entry.getY();
	}

	// --- Offset calculation ---

	@Override
	public MPPointF getOffset() {
		// When distanceAtBottom, center the bubble horizontally — no getLeft() reads needed
		if (distanceAtBottom) {
			return new MPPointF(-getWidth() / 2f, 0);
		}
		ChartData<?> chartData = getChartView().getData();
		int dataSetCount = chartData.getDataSetCount();
		if (dataSetCount > 0) {
			OrderedLineDataSet last = (OrderedLineDataSet) chartData.getDataSetByIndex(dataSetCount - 1);
			if (last.getDataSetType() == GPXDataSetType.ALTITUDE_EXTRM) {
				dataSetCount--;
			}
		}
		int halfDp = AndroidUtils.dpToPx(getContext(), .5f);
		float offsetX;
		if (dataSetCount == 2) {
			offsetX = -secondYAxisContainer.getLeft() - halfDp;
		} else if (dataSetCount == 1 && showXAxisValue) {
			offsetX = -xAxisContainer.getLeft() - halfDp;
		} else {
			offsetX = -getWidth() / 2f;
		}
		return new MPPointF(offsetX, 0);
	}

	@Override
	public MPPointF getOffsetForDrawingAtPoint(float posX, float posY) {
		MPPointF offset = getOffset();
		offset.y = getContentTopPx() - getBubbleHeight() - posY;
		if (!distanceAtBottom) {
			int margin = AndroidUtils.dpToPx(getContext(), 3f);
			if (posX + offset.x - margin < 0) {
				offset.x -= (offset.x + posX - margin);
			}
			if (posX + offset.x + getWidth() + margin > getChartView().getWidth()) {
				offset.x -= (getWidth() - (getChartView().getWidth() - posX) + offset.x) + margin;
			}
		}
		return offset;
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		if (distanceAtBottom && chartHeightForBottom > 0) {
			setMeasuredDimension(getMeasuredWidth(), chartHeightForBottom);
		}
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		super.onLayout(changed, l, t, r, b);
		if (distanceAtBottom && xAxisBottomContainer.getVisibility() == VISIBLE) {
			int contentBottom = getContentBottomPx();
			if (contentBottom > 0) {
				View frame = getChildAt(0);
				if (frame != null) {
					int bubbleHeight = getBubbleHeight();
					int frameHeight = (contentBottom - getContentTopPx() + bubbleHeight) + xAxisBottomContainer.getMeasuredHeight();
					frame.layout(frame.getLeft(), 0, frame.getRight(), frameHeight);
				}
			}
		}
	}

	private int getBubbleHeight() {
		ViewGroup frame = (ViewGroup) getChildAt(0);
		if (frame != null && frame.getChildCount() > 0) {
			return frame.getChildAt(0).getMeasuredHeight();
		}
		return 0;
	}

	private int getContentTopPx() {
		if (getChartView() instanceof BarLineChartBase) {
			float contentTop = ((BarLineChartBase<?>) getChartView()).getViewPortHandler().contentTop();
			if (contentTop >= 0) {
				return (int) contentTop;
			}
		}
		return 0;
	}

	private int getContentBottomPx() {
		if (getChartView() instanceof BarLineChartBase) {
			float contentBottom = ((BarLineChartBase<?>) getChartView()).getViewPortHandler().contentBottom();
			if (contentBottom > 0) {
				return (int) contentBottom;
			}
		}
		return chartHeightForBottom;
	}

	public boolean isTapOnBubble(float tapX, float tapY, @NonNull GPXHighlight highlight) {
		float[] offsets = bubbleOffsets.get(highlight);
		float topDx = offsets != null ? offsets[0] : 0f;
		RectF rect = getBubbleRect(true, highlight.getDrawX(), topDx, highlight);
		return !rect.isEmpty() && rect.contains(tapX, tapY);
	}

	@NonNull
	private OsmandApplication getMyApplication() {
		return ((OsmandApplication) getContext().getApplicationContext());
	}

	public interface MarkerValueFormatter {
		@NonNull
		String formatValue(@NonNull OsmandApplication app, float value);
	}
}
