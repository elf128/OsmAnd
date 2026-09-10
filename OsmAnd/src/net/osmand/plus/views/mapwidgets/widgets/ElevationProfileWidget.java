package net.osmand.plus.views.mapwidgets.widgets;

import static net.osmand.plus.mapcontextmenu.other.TrackDetailsMenu.ChartPointLayer.ROUTE;
import static net.osmand.plus.views.mapwidgets.WidgetType.ELEVATION_PROFILE;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.mikephil.charting.charts.ElevationChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.YAxis.AxisDependency;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.listener.ChartTouchListener.ChartGesture;
import com.github.mikephil.charting.listener.OnChartGestureListener;
import com.github.mikephil.charting.utils.Transformer;
import com.github.mikephil.charting.utils.ViewPortHandler;

import net.osmand.Location;
import net.osmand.StateChangedListener;
import net.osmand.data.LatLon;
import net.osmand.gpx.ElevationDiffsCalculator;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.charts.*;
import net.osmand.plus.mapcontextmenu.other.TrackDetailsMenu;
import net.osmand.plus.measurementtool.graph.BaseCommonChartAdapter;
import net.osmand.plus.routing.RouteCalculationResult;
import net.osmand.plus.settings.backend.ApplicationMode;
import net.osmand.plus.settings.backend.preferences.CommonPreference;
import net.osmand.plus.track.helpers.GpxDisplayItem;
import net.osmand.plus.track.helpers.GpxUiHelper;
import net.osmand.plus.utils.UiUtilities;
import net.osmand.plus.views.layers.base.OsmandMapLayer.DrawSettings;
import net.osmand.plus.views.mapwidgets.WidgetsContextMenu;
import net.osmand.plus.views.mapwidgets.WidgetsPanel;
import net.osmand.plus.views.mapwidgets.appearance.ResolvedPanelAppearance;
import net.osmand.plus.settings.enums.ScreenLayoutMode;
import net.osmand.shared.gpx.GpxFile;
import net.osmand.shared.gpx.GpxTrackAnalysis;
import net.osmand.shared.gpx.primitives.TrkSegment;
import net.osmand.shared.gpx.primitives.WptPt;
import net.osmand.util.Algorithms;
import net.osmand.util.MapUtils;

import java.util.ArrayList;
import java.util.List;

public class ElevationProfileWidget extends MapWidget {

	public enum CalculationMode {
		FROM_LOCATION, FROM_START, FROM_LEFT_EDGE
	}

	private static final String SHOW_SLOPE_PREF_ID = "show_slope_elevation_widget";
	private static final String SHOW_ELEVATION_MARKER_PREF_ID = "show_elevation_in_marker";
	private static final String SHOW_GAIN_DROP_MARKER_PREF_ID = "show_gain_drop_in_marker";
	private static final String SHOW_DISTANCE_MARKER_PREF_ID = "show_distance_in_marker";
	private static final String TWO_LINE_MARKER_PREF_ID = "two_line_marker";
	private static final String CALC_MODE_PREF_ID = "calc_mode_marker";
	private static final String ELEVATION_SMOOTHING_PREF_ID = "elevation_smoothing_widget";

	private final CommonPreference<Boolean> showSlopePreference;
	private final CommonPreference<Boolean> showElevationInMarkerPreference;
	private final CommonPreference<Boolean> showGainDropInMarkerPreference;
	private final CommonPreference<Boolean> showDistanceInMarkerPreference;
	private final CommonPreference<Boolean> twoLineMarkerPreference;
	private final CommonPreference<String> calculationModePreference;
	private final CommonPreference<Boolean> elevationSmoothingPreference;

	private static final int MAX_DISTANCE_TO_SHOW_IM_METERS = 10_000;

	private ElevationChart chart;

	private GpxDisplayItem gpxItem;
	private TrkSegment segment;
	private GpxFile gpx;
	private float toMetersMultiplier;
	private Location myLocation;
	private List<WptPt> allPoints;

	private boolean showSlopes;
	private RouteCalculationResult route;

	@Nullable
	private TrackChartPoints trackChartPoints;

	private boolean movedToLocation;
	private float tappedChartDataX = -1f;

	private static Matrix lastStateMatrix;
	private static String lastRoute;
	private static boolean lastChartLinkedToLocation;

	private final StateChangedListener<Boolean> linkedToLocationListener = change -> {
		if (change) {
			movedToLocation = true;
			lastChartLinkedToLocation = true;
		}
	};

	public ElevationProfileWidget(@NonNull MapActivity mapActivity, @Nullable String customId, @Nullable WidgetsPanel panel) {
		super(mapActivity, ELEVATION_PROFILE, customId, panel);
		this.showSlopePreference = registerShowSlopePref(customId);
		this.showElevationInMarkerPreference = registerBooleanMarkerPref(SHOW_ELEVATION_MARKER_PREF_ID, true, customId);
		this.showGainDropInMarkerPreference = registerBooleanMarkerPref(SHOW_GAIN_DROP_MARKER_PREF_ID, true, customId);
		this.showDistanceInMarkerPreference = registerBooleanMarkerPref(SHOW_DISTANCE_MARKER_PREF_ID, false, customId);
		this.twoLineMarkerPreference = registerBooleanMarkerPref(TWO_LINE_MARKER_PREF_ID, false, customId);
		this.calculationModePreference = registerStringMarkerPref(CALC_MODE_PREF_ID, CalculationMode.FROM_LOCATION.name(), customId);
		this.elevationSmoothingPreference = registerBooleanMarkerPref(ELEVATION_SMOOTHING_PREF_ID, false, customId);
		settings.MAP_LINKED_TO_LOCATION.addListener(linkedToLocationListener);
	}

	@Override
	protected void setupView(@NonNull View view) {
		super.setupView(view);
		updateVisibility(false);
		view.setOnLongClickListener(v -> {
			View anchor = chart != null ? chart : v;
			WidgetsContextMenu.showMenu(anchor, mapActivity, widgetType, customId, null,
					ScreenLayoutMode.getDefault(v.getContext()), panel, nightMode, true);
			return true;
		});
	}

	public Boolean shouldShowSlope(@NonNull ApplicationMode appMode) {
		return showSlopePreference.getModeValue(appMode);
	}

	public void setShouldShowSlope(@NonNull ApplicationMode appMode, boolean shouldShowSlope) {
		showSlopePreference.setModeValue(appMode, shouldShowSlope);
	}

	@NonNull
	private CommonPreference<Boolean> registerShowSlopePref(@Nullable String customId) {
		String prefId = Algorithms.isEmpty(customId) ? SHOW_SLOPE_PREF_ID : SHOW_SLOPE_PREF_ID + customId;
		return settings.registerBooleanPreference(prefId, false)
				.makeProfile()
				.cache();
	}

	@NonNull
	private CommonPreference<Boolean> registerBooleanMarkerPref(@NonNull String prefId, boolean defaultValue, @Nullable String customId) {
		String id = Algorithms.isEmpty(customId) ? prefId : prefId + customId;
		return settings.registerBooleanPreference(id, defaultValue)
				.makeProfile()
				.cache();
	}

	@NonNull
	private CommonPreference<String> registerStringMarkerPref(@NonNull String prefId, @NonNull String defaultValue, @Nullable String customId) {
		String id = Algorithms.isEmpty(customId) ? prefId : prefId + customId;
		return settings.registerStringPreference(id, defaultValue)
				.makeProfile()
				.cache();
	}

	public boolean shouldShowElevationInMarker(@NonNull ApplicationMode appMode) {
		return showElevationInMarkerPreference.getModeValue(appMode);
	}

	public void setShowElevationInMarker(@NonNull ApplicationMode appMode, boolean show) {
		showElevationInMarkerPreference.setModeValue(appMode, show);
		applyMarkerPrefs();
	}

	public boolean shouldShowGainDropInMarker(@NonNull ApplicationMode appMode) {
		return showGainDropInMarkerPreference.getModeValue(appMode);
	}

	public void setShowGainDropInMarker(@NonNull ApplicationMode appMode, boolean show) {
		showGainDropInMarkerPreference.setModeValue(appMode, show);
		applyMarkerPrefs();
	}

	public boolean shouldShowDistanceInMarker(@NonNull ApplicationMode appMode) {
		return showDistanceInMarkerPreference.getModeValue(appMode);
	}

	public void setShowDistanceInMarker(@NonNull ApplicationMode appMode, boolean show) {
		showDistanceInMarkerPreference.setModeValue(appMode, show);
		applyMarkerPrefs();
	}

	public boolean shouldTwoLineMarker(@NonNull ApplicationMode appMode) {
		return twoLineMarkerPreference.getModeValue(appMode);
	}

	public void setTwoLineMarker(@NonNull ApplicationMode appMode, boolean twoLine) {
		twoLineMarkerPreference.setModeValue(appMode, twoLine);
		applyMarkerPrefs();
	}

	@NonNull
	public CalculationMode getCalculationMode(@NonNull ApplicationMode appMode) {
		try {
			return CalculationMode.valueOf(calculationModePreference.getModeValue(appMode));
		} catch (IllegalArgumentException e) {
			return CalculationMode.FROM_LOCATION;
		}
	}

	public void setCalculationMode(@NonNull ApplicationMode appMode, @NonNull CalculationMode mode) {
		calculationModePreference.setModeValue(appMode, mode.name());
		applyMarkerPrefs();
	}

	public boolean isElevationSmoothing(@NonNull ApplicationMode appMode) {
		return elevationSmoothingPreference.getModeValue(appMode);
	}

	public void setElevationSmoothing(@NonNull ApplicationMode appMode, boolean smooth) {
		elevationSmoothingPreference.setModeValue(appMode, smooth);
		applyMarkerPrefs();
	}

	private void applyMarkerPrefs() {
		if (chart == null || !(chart.getMarker() instanceof GpxMarkerView)) {
			return;
		}
		GpxMarkerView marker = (GpxMarkerView) chart.getMarker();
		marker.setShowElevation(showElevationInMarkerPreference.get());
		marker.setShowGainDrop(showGainDropInMarkerPreference.get());
		marker.setShowDistance(showDistanceInMarkerPreference.get());
		marker.setTwoLineMode(twoLineMarkerPreference.get());

		if (tappedChartDataX >= 0 && touchHighlight != null) {
			updateSegmentDiffs(touchHighlight, tappedChartDataX);
		}
		updateWaypointDiffs();
		refreshHighlights();
	}

	private void restoreLastState() {
		if (chart != null && lastStateMatrix != null && route != null) {
			if (Algorithms.stringsEqual(lastRoute, route.toString())) {
				chart.getViewPortHandler().refresh(new Matrix(lastStateMatrix), chart, false);
			} else {
				lastStateMatrix = null;
			}
			if (lastChartLinkedToLocation) {
				movedToLocation = true;
			}
		}
	}

	private void storeLastState(boolean chartLinkedToLocation) {
		if (chart != null) {
			lastStateMatrix = new Matrix(chart.getViewPortHandler().getMatrixTouch());
		}
		lastChartLinkedToLocation = chartLinkedToLocation;
	}

	@Override
	protected int getLayoutId() {
		return R.layout.elevation_profile_widget;
	}

	@Override
	public void updateInfo(@NonNull View view, @Nullable DrawSettings drawSettings) {
		boolean visible = visibilityHelper.shouldShowElevationProfileWidget();
		updateVisibility(visible);
		if (visible) {
			updateInfoImpl();
		} else {
			clearTrackChartPoints();
		}
	}

	private void updateInfoImpl() {
		boolean settingsUpdated = updateSettings();
		if (settingsUpdated) {
			setupChart();
		}
		boolean chartUpdated = updateChart(settingsUpdated);
		if (chartUpdated) {
			updateWidgets();
		}
		if (settingsUpdated) {
			View view = getView();
			view.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
				@Override
				public void onGlobalLayout() {
					view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
					restoreLastState();
				}
			});
		}
	}

	@Override
	protected void onPanelAppearanceChanged(@NonNull ResolvedPanelAppearance appearance) {
		super.onPanelAppearanceChanged(appearance);
		int primaryTextColor = appearance.getPrimaryTextColor();
		getView().findViewById(R.id.elevation_profile_widget_background).setBackgroundColor(appearance.getBackground().getColor());
		if (chart != null) {
			updateChartAppearance(chart);
		}
	}

	private boolean updateSettings() {
		RouteCalculationResult route = app.getRoutingHelper().getRoute();
		boolean routeChanged = this.route != route;
		this.route = route;
		lastRoute = route.toString();
		boolean showSlopes = showSlopePreference.get();
		boolean slopesChanged = showSlopes != this.showSlopes;
		this.showSlopes = showSlopes;
		return routeChanged || slopesChanged;
	}

	private void clearTrackChartPoints() {
		if (trackChartPoints != null) {
			mapActivity.getMapLayers().getRouteLayer().setTrackChartPoints(null);
			trackChartPoints = null;
			mapActivity.refreshMap();
		}
	}

	private void setupChart() {
		clearTrackChartPoints();
		tappedChartDataX = -1f;
		gpx = GpxUiHelper.makeGpxFromLocations(route.getImmutableAllLocations(), app);
		GpxTrackAnalysis analysis = gpx.getAnalysis(0);
		allPoints = gpx.getAllSegmentsPoints();
		gpxItem = GpxUiHelper.makeGpxDisplayItem(app, gpx, ROUTE, analysis);

		chart = getView().findViewById(R.id.line_chart);
		BaseCommonChartAdapter chartAdapter = new BaseCommonChartAdapter(app, chart, true);
		updateChartAppearance(chart);

		if (analysis.hasElevationData()) {
			List<ILineDataSet> dataSets = new ArrayList<>();
			OrderedLineDataSet elevationDataSet = ChartUtils.createGPXElevationDataSet(app, chart, analysis,
					GPXDataSetType.ALTITUDE, GPXDataSetAxisType.DISTANCE, false, true, false);
			dataSets.add(elevationDataSet);

			if (showSlopes) {
				OrderedLineDataSet slopeDataSet = ChartUtils.createGPXSlopeDataSet(app, chart, analysis,
						GPXDataSetType.SLOPE, GPXDataSetAxisType.DISTANCE, elevationDataSet.getEntries(), true, true, false);
				if (slopeDataSet != null) {
					dataSets.add(slopeDataSet);
				}
			}

			chartAdapter.updateContent(new LineData(dataSets), gpxItem);
			toMetersMultiplier = ((OrderedLineDataSet) dataSets.get(0)).getDivX();

			setupZoom(chart);
			setupWaypointHighlights();
			chart.setVisibility(View.VISIBLE);
		} else {
			chart.setVisibility(View.GONE);
		}
		segment = TrackDetailsMenu.getTrackSegment(chart, gpxItem);
		chart.setOnChartGestureListener(new OnChartGestureListener() {
			boolean hasTranslated;
			float highlightDrawX = -1;

			@Override
			public void onChartGestureStart(MotionEvent me, ChartGesture lastPerformedGesture) {
				hasTranslated = false;
				GPXHighlight touch = touchHighlight;
				highlightDrawX = touch != null ? touch.getDrawX() : -1;
			}

			@Override
			public void onChartGestureEnd(MotionEvent me, ChartGesture lastPerformedGesture) {
				gpxItem.chartMatrix = new Matrix(chart.getViewPortHandler().getMatrixTouch());
				storeLastState(false);
				app.runInUIThread(() -> updateWidgets());
			}

			@Override
			public void onChartLongPressed(MotionEvent me) {
				WidgetsContextMenu.showMenu(chart, mapActivity, widgetType, customId, null,
						ScreenLayoutMode.getDefault(chart.getContext()), panel, nightMode, true);
			}

			@Override
			public void onChartDoubleTapped(MotionEvent me) {
			}

			@Override
			public void onChartSingleTapped(MotionEvent me) {
				Highlight raw = chart.getHighlightByTouchPoint(me.getX(), me.getY());
				if (raw != null) {
					GPXHighlight newTouch = createGPXHighlight(raw.getX(), false);
					tappedChartDataX = newTouch.getX();
					updateSegmentDiffs(newTouch, tappedChartDataX);
					touchHighlight = newTouch;
				}
				refreshHighlights();
			}

			@Override
			public void onChartFling(MotionEvent me1, MotionEvent me2, float velocityX, float velocityY) {
			}

			@Override
			public void onChartScale(MotionEvent me, float scaleX, float scaleY) {
				app.runInUIThread(() -> updateWidgets());
			}

			@Override
			public void onChartTranslate(MotionEvent me, float dX, float dY) {
				hasTranslated = true;
				if (highlightDrawX != -1) {
					Highlight raw = chart.getHighlightByTouchPoint(highlightDrawX, 0f);
					if (raw != null) {
						GPXHighlight newTouch = createGPXHighlight(raw.getX(), false);
						tappedChartDataX = newTouch.getX();
						updateSegmentDiffs(newTouch, tappedChartDataX);
						touchHighlight = newTouch;
						refreshHighlights();
					}
				}
				app.runInUIThread(() -> updateWidgets());
			}
		});
	}

	private void updateChartAppearance(@NonNull ElevationChart chart) {
		UiUtilities iconsCache = app.getUIUtilities();
		ApplicationMode appMode = settings.getApplicationMode();
		int profileColor = appMode.getProfileColor(isNightMode());
		Context themedContext = UiUtilities.getThemedContext(chart.getContext(), nightMode);
		Drawable markerIcon = iconsCache.getPaintedIcon(R.drawable.ic_action_location_color, profileColor);
		Drawable waypointIcon = iconsCache.getPaintedIcon(R.drawable.ic_action_flag,
				app.getResources().getColor(R.color.gpx_chart_orange_label, null));
		Drawable destinationIcon = iconsCache.getPaintedIcon(R.drawable.ic_action_finish_navigation, profileColor);

		ElevationChartAppearance appearance = new ElevationChartAppearance();
		appearance.setContext(themedContext);
		appearance.setMarkerIcon(markerIcon);
		appearance.setTopOffset(48f);
		ChartUtils.setupElevationChart(chart, appearance);

		if (chart.getMarker() instanceof GpxMarkerView) {
			GpxMarkerView marker = (GpxMarkerView) chart.getMarker();
			marker.setDistanceAtBottom(true);
			marker.setWaypointIcon(waypointIcon);
			marker.setDestinationIcon(destinationIcon);
		}
		applyMarkerPrefs();

		chart.setHighlightPerTapEnabled(false);
		chart.setHighlightPerDragEnabled(false);
	}

	@Nullable
	private GPXHighlight locationHighlight;
	@Nullable
	private GPXHighlight touchHighlight;
	private List<GPXHighlight> waypointHighlights = new ArrayList<>();

	private boolean updateChart(boolean forceUpdate) {
		Location location = app.getLocationProvider().getLastKnownLocation();
		if (!forceUpdate && myLocation != null && MapUtils.areLatLonEqual(myLocation, location)) {
			return false;
		}
		myLocation = location;
		if (location == null) {
			gpxItem.chartHighlightPos = -1f;
			locationHighlight = null;
			refreshHighlights();
			return true;
		}
		LineData lineData = chart.getLineData();
		List<ILineDataSet> ds = lineData != null ? lineData.getDataSets() : null;
		RouteCalculationResult route = this.route;
		TrkSegment segment = this.segment;
		if (Algorithms.isEmpty(ds) || gpxItem == null || route == null || segment == null) {
			return false;
		}
		float distanceFromStart = route.getDistanceFromStart();
		if (distanceFromStart == 0) {
			gpxItem.chartHighlightPos = -1f;
			locationHighlight = null;
			refreshHighlights();
			return true;
		}
		float minVisibleX = chart.getLowestVisibleX();
		float maxVisibleX = chart.getHighestVisibleX();
		float twentyPercent = ((maxVisibleX - minVisibleX) / 5);
		float startMoveChartPosition = minVisibleX + twentyPercent;
		float pos = distanceFromStart / ((OrderedLineDataSet) ds.get(0)).getDivX();

		boolean movedToLocation = this.movedToLocation;
		if (pos >= minVisibleX && pos <= maxVisibleX || movedToLocation) {
			if (pos >= startMoveChartPosition) {
				float nextVisibleX = pos - twentyPercent;
				moveViewToX(chart, nextVisibleX);
			} else if (movedToLocation) {
				moveViewToX(chart, Math.max(pos - twentyPercent, chart.getXChartMin()));
			}
			if (movedToLocation) {
				this.movedToLocation = false;
			}
			gpxItem.chartHighlightPos = pos;
			GPXHighlight newLocationHighlight = createGPXHighlight(pos, true);
			updateLocationDiffs(newLocationHighlight, pos);
			locationHighlight = newLocationHighlight;
			updateWaypointDiffs();
			if (tappedChartDataX >= 0 && touchHighlight != null) {
				CalculationMode mode;
				try {
					mode = CalculationMode.valueOf(calculationModePreference.get());
				} catch (IllegalArgumentException e) {
					mode = CalculationMode.FROM_LOCATION;
				}
				if (mode == CalculationMode.FROM_LOCATION) {
					updateSegmentDiffs(touchHighlight, tappedChartDataX);
				}
			}
			refreshHighlights();
			storeLastState(true);
		}
		return true;
	}

	private void refreshHighlights() {
		List<Highlight> list = new ArrayList<>(waypointHighlights);
		if (locationHighlight != null) {
			list.add(locationHighlight);
		}
		if (touchHighlight != null) {
			list.add(touchHighlight);
		}
		chart.highlightValues(list.isEmpty() ? null : list.toArray(new Highlight[0]));
	}

	private GPXHighlight createGPXHighlight(float x, boolean location) {
		return new GPXHighlight(x, 0, location);
	}

	private void runElevationDiffs(@NonNull ElevationDiffsCalculator calc) {
		if (elevationSmoothingPreference.get()) {
			calc.calculateElevationDiffs();
		} else {
			calc.calculateElevationDiffsRaw();
		}
	}

	private void updateLocationDiffs(@NonNull GPXHighlight highlight, float toChartX) {
		List<WptPt> points = allPoints;
		if (points == null || points.isEmpty()) {
			highlight.setUnavailable();
			return;
		}
		if (toChartX <= 0) {
			highlight.setDiffs(0, 0);
			return;
		}
		double toDist = toChartX * toMetersMultiplier;
		int toIndex = gpx.getPointIndexByDistance(points, toDist);
		if (toIndex < 1) {
			highlight.setDiffs(0, 0);
			return;
		}
		final int count = toIndex + 1;
		final List<WptPt> pts = points;
		ElevationDiffsCalculator calc = new ElevationDiffsCalculator() {
			@Override
			public double getPointDistance(int index) {
				return pts.get(index).getDistance();
			}
			@Override
			public double getPointElevation(int index) {
				return pts.get(index).getEle();
			}
			@Override
			public int getPointsCount() {
				return count;
			}
		};
		runElevationDiffs(calc);
		highlight.setDiffs(calc.getDiffElevationUp(), calc.getDiffElevationDown());
	}

	private void updateSegmentDiffs(@NonNull GPXHighlight highlight, float tappedChartX) {
		List<WptPt> points = allPoints;
		if (points == null || points.isEmpty()) {
			highlight.setUnavailable();
			return;
		}

		CalculationMode mode;
		try {
			mode = CalculationMode.valueOf(calculationModePreference.get());
		} catch (IllegalArgumentException e) {
			mode = CalculationMode.FROM_LOCATION;
		}

		Boolean isAhead;
		float fromChartX;
		switch (mode) {
			case FROM_LOCATION:
				float pos = gpxItem != null ? gpxItem.chartHighlightPos : -1f;
				if (pos <= 0) {
					highlight.setUnavailable();
					return;
				}
				isAhead = tappedChartX >= pos;
				fromChartX = pos;
				break;
			case FROM_LEFT_EDGE:
				isAhead = null;
				fromChartX = chart.getLowestVisibleX();
				break;
			default: // FROM_START
				isAhead = null;
				fromChartX = 0f;
		}

		double fromDist = fromChartX * toMetersMultiplier;
		double toDist = tappedChartX * toMetersMultiplier;

		// For FROM_LOCATION and FROM_LEFT_EDGE, show relative distance; FROM_START uses entry.x.
		if (mode == CalculationMode.FROM_LOCATION || mode == CalculationMode.FROM_LEFT_EDGE) {
			highlight.setDistanceM(Math.abs(toDist - fromDist));
		} else {
			highlight.setDistanceM(Double.NaN);
		}
		highlight.setIsAhead(isAhead);

		int fromIndex = gpx.getPointIndexByDistance(points, fromDist);
		int toIndex = gpx.getPointIndexByDistance(points, toDist);
		if (fromIndex > toIndex) {
			int tmp = fromIndex;
			fromIndex = toIndex;
			toIndex = tmp;
		}
		final int startIdx = fromIndex;
		final int count = toIndex - fromIndex + 1;
		if (count < 2) {
			return;
		}
		final List<WptPt> pts = points;
		ElevationDiffsCalculator calc = new ElevationDiffsCalculator() {
			@Override
			public double getPointDistance(int index) {
				return pts.get(startIdx + index).getDistance();
			}
			@Override
			public double getPointElevation(int index) {
				return pts.get(startIdx + index).getEle();
			}
			@Override
			public int getPointsCount() {
				return count;
			}
		};
		runElevationDiffs(calc);
		highlight.setDiffs(calc.getDiffElevationUp(), calc.getDiffElevationDown());
	}

	private void setupWaypointHighlights() {
		waypointHighlights.clear();
		if (route == null || toMetersMultiplier == 0) {
			return;
		}
		List<Float> distances = route.getIntermediateDistancesFromStart();
		for (float distM : distances) {
			float chartX = distM / toMetersMultiplier;
			waypointHighlights.add(new GPXHighlight(chartX, 0, false, true));
		}
		float destChartX = chart.getXChartMax();
		waypointHighlights.add(new GPXHighlight(destChartX, 0, false, true, true));
		updateWaypointDiffs();
	}

	private void updateWaypointDiffs() {
		for (GPXHighlight h : waypointHighlights) {
			updateSegmentDiffs(h, h.getX());
		}
	}

	private boolean updateWidgets() {
		updateTrackChartPoints();
		return false;
	}

	private void updateTrackChartPoints() {
		Highlight highlight = getSelectedHighlight();
		if (highlight != null) {
			TrackChartPoints trackChartPoints = getTrackChartPoints();
			LatLon location = TrackDetailsMenu.getLocationAtPos(chart, gpxItem, segment, highlight.getX(), true);
			if (location != null) {
				trackChartPoints.setHighlightedPoint(location);
			}
			if (gpxItem.chartPointLayer == ROUTE) {
				mapActivity.getMapLayers().getRouteLayer().setTrackChartPoints(trackChartPoints);
			}
			if (location != null) {
				mapActivity.refreshMap();
			}
		}
	}

	@NonNull
	private TrackChartPoints getTrackChartPoints() {
		TrackChartPoints trackChartPoints = this.trackChartPoints;
		if (trackChartPoints == null) {
			trackChartPoints = new TrackChartPoints();
			int segmentColor = segment != null ? segment.getColor(0) : 0;
			trackChartPoints.setSegmentColor(segmentColor);
			trackChartPoints.setGpx(gpxItem.group.getGpxFile());
			this.trackChartPoints = trackChartPoints;
		}
		return trackChartPoints;
	}

	@Nullable
	private Highlight getSelectedHighlight() {
		return touchHighlight;
	}

	private void setupZoom(LineChart chart) {
		chart.fitScreen();
		float maxValue = chart.getXChartMax() * toMetersMultiplier;
		if (maxValue > MAX_DISTANCE_TO_SHOW_IM_METERS) {
			float scaleX = maxValue / MAX_DISTANCE_TO_SHOW_IM_METERS;
			chart.zoom(scaleX, 1.0f, 0, 0);
			chart.scrollTo(0, 0);
		}
	}

	private static void moveViewToX(LineChart chart, float nextVisibleX) {
		ViewPortHandler handler = chart.getViewPortHandler();
		Transformer transformer = chart.getTransformer(AxisDependency.LEFT);

		float[] pts = new float[2];
		pts[0] = nextVisibleX;
		pts[1] = 0f;
		transformer.pointValuesToPixel(pts);

		Matrix save = new Matrix();
		save.reset();
		save.set(handler.getMatrixTouch());

		float x = pts[0] - handler.offsetLeft();
		float y = pts[1] - handler.offsetTop();
		save.postTranslate(-x, -y);
		handler.refresh(save, chart, false);
	}
}
