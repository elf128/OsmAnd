package net.osmand.plus.charts;

import static android.text.format.DateUtils.SECOND_IN_MILLIS;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
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
import java.util.List;

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

	// Bottom distance section
	private final View xAxisBottomContainer;
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

	// Segment diffs state — tapped marker (calc-mode dependent)
	private double segmentGainM = Double.NaN;
	private double segmentDropM = Double.NaN;
	private boolean gpsUnavailable = false;

	// Segment diffs state — GPS location marker (always FROM_START)
	private double locationGainM = Double.NaN;
	private double locationDropM = Double.NaN;
	private boolean locationGpsUnavailable = false;

	// Chart height for bottom-container positioning (read from dimen on setDistanceAtBottom)
	private int chartHeightForBottom = 0;

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
		((ImageView) findViewById(R.id.icon)).setImageDrawable(icon);
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
					.getDimension(R.dimen.route_info_line_chart_height);
		}
	}

	// --- Data setters ---

	public void setSegmentDiffs(double gainM, double dropM) {
		segmentGainM = gainM;
		segmentDropM = dropM;
		gpsUnavailable = false;
	}

	public void setSegmentDiffsUnavailable() {
		segmentGainM = 0;
		segmentDropM = 0;
		gpsUnavailable = true;
	}

	public void setLocationDiffs(double gainM, double dropM) {
		locationGainM = gainM;
		locationDropM = dropM;
		locationGpsUnavailable = false;
	}

	public void setLocationDiffsUnavailable() {
		locationGainM = 0;
		locationDropM = 0;
		locationGpsUnavailable = true;
	}

	// --- refreshContent ---

	@Override
	public void refreshContent(@NonNull Entry entry, @NonNull Highlight highlight) {
		ChartData<?> chartData = getChartView().getData();
		boolean isLocationHighlight = highlight instanceof GPXHighlight
				&& ((GPXHighlight) highlight).shouldShowLocationIcon();

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

		// Update x-axis content for both inline and bottom containers
		if (firstDataSet != null) {
			updateXAxisContent(firstDataSet, entry);
		}

		// Select the appropriate diffs pair for this highlight type
		double currentGainM = isLocationHighlight ? locationGainM : segmentGainM;
		double currentDropM = isLocationHighlight ? locationDropM : segmentDropM;
		boolean currentGpsUnavail = isLocationHighlight ? locationGpsUnavailable : gpsUnavailable;
		boolean hasDiffsData = !Double.isNaN(currentGainM) || currentGpsUnavail;

		if (hasDiffsData) {
			OsmandApplication app = getMyApplication();
			segmentGainText.setText(currentGpsUnavail ? "↑ -" : "↑ " + OsmAndFormatter.getFormattedAlt(currentGainM, app));
			segmentDropText.setText(currentGpsUnavail ? "↓ -" : "↓ " + OsmAndFormatter.getFormattedAlt(currentDropM, app));
		}

		// Bottom distance container — visibility only; height is handled by onMeasure()
		if (distanceAtBottom) {
			boolean isDistType = firstDataSet != null
					&& firstDataSet.getDataSetAxisType() == GPXDataSetAxisType.DISTANCE;
			xAxisBottomContainer.setVisibility(showDistance && isDistType ? VISIBLE : GONE);
		}

		// Arrange items into rows
		if (twoLineMode) {
			arrangeTwoLines(isLocationHighlight, dataSetCount, firstDataSet, hasDiffsData);
		} else {
			arrangeSingleLine(isLocationHighlight, dataSetCount, firstDataSet, hasDiffsData);
		}

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
	private void updateXAxisContent(@NonNull OrderedLineDataSet dataSet, @NonNull Entry entry) {
		GPXDataSetAxisType xAxisType = dataSet.getDataSetAxisType();
		if (xAxisType == GPXDataSetAxisType.DISTANCE) {
			float meters = entry.getX() * dataSet.getDivX();
			FormattedValue fv = OsmAndFormatter.getFormattedDistanceValue(meters, getMyApplication());
			String valueStr = fv.value + " ";
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
	private void arrangeSingleLine(boolean isLocationHighlight, int dataSetCount,
	                               @Nullable OrderedLineDataSet firstDataSet, boolean hasDiffsData) {
		bubbleRow0.removeAllViews();
		bubbleRow1.removeAllViews();
		rowDivider.setVisibility(GONE);
		bubbleRow1.setVisibility(GONE);
		iconDivider.setVisibility(GONE);

		boolean showIcon = hasIcon && isLocationHighlight;
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
	private void arrangeTwoLines(boolean isLocationHighlight, int dataSetCount,
	                             @Nullable OrderedLineDataSet firstDataSet, boolean hasDiffsData) {
		bubbleRow0.removeAllViews();
		bubbleRow1.removeAllViews();
		iconDivider.setVisibility(GONE);

		boolean hasIconItem = hasIcon && isLocationHighlight;
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
		int margin = AndroidUtils.dpToPx(getContext(), 3f);
		MPPointF offset = getOffset();
		offset.y = -posY;
		if (posX + offset.x - margin < 0) {
			offset.x -= (offset.x + posX - margin);
		}
		if (posX + offset.x + getWidth() + margin > getChartView().getWidth()) {
			offset.x -= (getWidth() - (getChartView().getWidth() - posX) + offset.x) + margin;
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
					// Expand the inner FrameLayout so layout_gravity="bottom" places the container
					// at contentBottom (just into the X axis zone), within the frame's own bounds.
					// Container top = frameHeight - containerHeight = contentBottom.
					int frameHeight = contentBottom + xAxisBottomContainer.getMeasuredHeight();
					frame.layout(frame.getLeft(), 0, frame.getRight(), frameHeight);
				}
			}
		}
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

	@NonNull
	private OsmandApplication getMyApplication() {
		return ((OsmandApplication) getContext().getApplicationContext());
	}

	public interface MarkerValueFormatter {
		@NonNull
		String formatValue(@NonNull OsmandApplication app, float value);
	}
}
