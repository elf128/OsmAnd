package net.osmand.plus.views.mapwidgets.configure.settings;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import net.osmand.plus.R;
import net.osmand.plus.utils.ColorUtilities;
import net.osmand.plus.utils.UiUtilities;
import net.osmand.plus.utils.UiUtilities.CompoundButtonType;
import net.osmand.plus.views.mapwidgets.WidgetType;
import net.osmand.plus.views.mapwidgets.widgets.ElevationProfileWidget;
import net.osmand.plus.views.mapwidgets.widgets.ElevationProfileWidget.CalculationMode;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;

public class ElevationProfileWidgetInfoFragment extends WidgetInfoBaseFragment {

	private static final String KEY_SHOW_SLOPE = "show_slope";
	private static final String KEY_SHOW_ELEVATION = "show_elevation_in_marker";
	private static final String KEY_SHOW_GAIN_DROP = "show_gain_drop_in_marker";
	private static final String KEY_SHOW_DISTANCE = "show_distance_in_marker";
	private static final String KEY_TWO_LINE = "two_line_marker";
	private static final String KEY_CALC_MODE = "calc_mode";
	private static final String KEY_SMOOTHING = "elevation_smoothing";

	private ElevationProfileWidget elevationWidget;

	private boolean showSlope;
	private boolean showElevation;
	private boolean showGainDrop;
	private boolean showDistance;
	private boolean twoLine;
	private String calcMode;
	private boolean smoothing;

	private ImageView slopeIcon;

	@NonNull
	@Override
	public WidgetType getWidget() {
		return WidgetType.ELEVATION_PROFILE;
	}

	@Override
	protected void initParams(@NonNull Bundle bundle) {
		super.initParams(bundle);

		if (bundle.containsKey(KEY_SHOW_SLOPE)) {
			showSlope = bundle.getBoolean(KEY_SHOW_SLOPE);
			showElevation = bundle.getBoolean(KEY_SHOW_ELEVATION, true);
			showGainDrop = bundle.getBoolean(KEY_SHOW_GAIN_DROP, true);
			showDistance = bundle.getBoolean(KEY_SHOW_DISTANCE, false);
			twoLine = bundle.getBoolean(KEY_TWO_LINE, false);
			calcMode = bundle.getString(KEY_CALC_MODE, CalculationMode.FROM_LOCATION.name());
			smoothing = bundle.getBoolean(KEY_SMOOTHING, false);
		} else if (widgetInfo != null) {
			elevationWidget = (ElevationProfileWidget) widgetInfo.widget;
			showSlope = elevationWidget.shouldShowSlope(appMode);
			showElevation = elevationWidget.shouldShowElevationInMarker(appMode);
			showGainDrop = elevationWidget.shouldShowGainDropInMarker(appMode);
			showDistance = elevationWidget.shouldShowDistanceInMarker(appMode);
			twoLine = elevationWidget.shouldTwoLineMarker(appMode);
			calcMode = elevationWidget.getCalculationMode(appMode).name();
			smoothing = elevationWidget.isElevationSmoothing(appMode);
		}
	}

	@Override
	protected void setupMainContent(@NonNull ViewGroup container) {
		View content = inflate(R.layout.elevation_profile_widget_settings, container, false);
		container.addView(content);

		setupSlopeToggle();
		setupToggle(R.id.elevation_in_marker_row, R.id.elevation_in_marker_icon,
				R.id.elevation_in_marker_switch, R.drawable.ic_action_altitude,
				() -> showElevation, v -> showElevation = v);
		setupToggle(R.id.gain_drop_in_marker_row, R.id.gain_drop_in_marker_icon,
				R.id.gain_drop_in_marker_switch, R.drawable.ic_action_altitude_ascent,
				() -> showGainDrop, v -> showGainDrop = v);
		setupToggle(R.id.distance_in_marker_row, R.id.distance_in_marker_icon,
				R.id.distance_in_marker_switch, R.drawable.ic_action_distance,
				() -> showDistance, v -> showDistance = v);
		setupToggle(R.id.two_line_marker_row, R.id.two_line_marker_icon,
				R.id.two_line_marker_switch, R.drawable.ic_action_altitude_range,
				() -> twoLine, v -> twoLine = v);
		setupToggle(R.id.elevation_smoothing_row, R.id.elevation_smoothing_icon,
				R.id.elevation_smoothing_switch, R.drawable.ic_action_filter,
				() -> smoothing, v -> smoothing = v);
		setupCalcModeRadios();
	}

	private void setupSlopeToggle() {
		slopeIcon = view.findViewById(R.id.slope_icon);
		SwitchCompat slopeSwitch = view.findViewById(R.id.slope_switch);

		updateSlopeIcon(showSlope);
		UiUtilities.setupCompoundButton(slopeSwitch, nightMode, CompoundButtonType.GLOBAL);
		slopeSwitch.setChecked(showSlope);
		slopeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
			showSlope = isChecked;
			updateSlopeIcon(showSlope);
		});

		View row = (View) slopeSwitch.getParent();
		row.setOnClickListener(v -> slopeSwitch.setChecked(!showSlope));
		row.setBackground(getPressedStateDrawable());
	}

	private void setupToggle(int rowId, int iconId, int switchId, int iconResId,
	                          BooleanSupplier getter, BooleanConsumer setter) {
		ImageView icon = view.findViewById(iconId);
		SwitchCompat sw = view.findViewById(switchId);
		View row = view.findViewById(rowId);

		Drawable drawable = getIcon(iconResId,
				getter.get()
						? ColorUtilities.getActiveIconColorId(nightMode)
						: ColorUtilities.getDefaultIconColorId(nightMode));
		icon.setImageDrawable(drawable);

		UiUtilities.setupCompoundButton(sw, nightMode, CompoundButtonType.GLOBAL);
		sw.setChecked(getter.get());
		sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
			setter.set(isChecked);
			icon.setImageDrawable(getIcon(iconResId,
					isChecked
							? ColorUtilities.getActiveIconColorId(nightMode)
							: ColorUtilities.getDefaultIconColorId(nightMode)));
		});

		row.setOnClickListener(v -> sw.setChecked(!getter.get()));
		row.setBackground(getPressedStateDrawable());
	}

	private void setupCalcModeRadios() {
		setupCalcRadio(R.id.calc_from_location_container,
				R.drawable.ic_action_location_color,
				R.string.shared_string_my_location,
				CalculationMode.FROM_LOCATION);
		setupCalcRadio(R.id.calc_from_start_container,
				R.drawable.ic_action_flag,
				R.string.route_start_point,
				CalculationMode.FROM_START);
		setupCalcRadio(R.id.calc_from_left_edge_container,
				R.drawable.ic_action_ruler_line,
				R.string.elevation_profile_visible_graph_range,
				CalculationMode.FROM_LEFT_EDGE);
	}

	private void setupCalcRadio(int containerId, int iconResId, int titleResId,
	                             CalculationMode mode) {
		View container = view.findViewById(containerId);
		ImageView icon = container.findViewById(R.id.icon);
		TextView title = container.findViewById(R.id.title);
		CompoundButton radio = container.findViewById(R.id.compound_button);

		icon.setImageDrawable(getIcon(iconResId, ColorUtilities.getDefaultIconColorId(nightMode)));
		title.setText(titleResId);

		UiUtilities.setupCompoundButton(radio, nightMode, CompoundButtonType.GLOBAL);
		radio.setChecked(mode.name().equals(calcMode));
		radio.setOnCheckedChangeListener((buttonView, isChecked) -> {
			if (isChecked) {
				calcMode = mode.name();
				deselectOtherRadios(containerId);
			}
		});

		container.setOnClickListener(v -> radio.setChecked(true));
		container.setBackground(getPressedStateDrawable());
	}

	private void deselectOtherRadios(int selectedContainerId) {
		int[] allContainerIds = {
				R.id.calc_from_location_container,
				R.id.calc_from_start_container,
				R.id.calc_from_left_edge_container
		};
		for (int id : allContainerIds) {
			if (id != selectedContainerId) {
				View container = view.findViewById(id);
				if (container != null) {
					CompoundButton radio = container.findViewById(R.id.compound_button);
					if (radio != null && radio.isChecked()) {
						radio.setChecked(false);
					}
				}
			}
		}
	}

	private void updateSlopeIcon(boolean active) {
		Drawable drawable = active
				? getIcon(R.drawable.ic_action_slope, ColorUtilities.getActiveIconColorId(nightMode))
				: getIcon(R.drawable.ic_action_slope_hide, ColorUtilities.getDefaultIconColorId(nightMode));
		slopeIcon.setImageDrawable(drawable);
	}

	@Override
	protected void applySettings() {
		if (elevationWidget != null) {
			elevationWidget.setShouldShowSlope(appMode, showSlope);
			elevationWidget.setShowElevationInMarker(appMode, showElevation);
			elevationWidget.setShowGainDropInMarker(appMode, showGainDrop);
			elevationWidget.setShowDistanceInMarker(appMode, showDistance);
			elevationWidget.setTwoLineMarker(appMode, twoLine);
			try {
				elevationWidget.setCalculationMode(appMode, CalculationMode.valueOf(calcMode));
			} catch (IllegalArgumentException e) {
				elevationWidget.setCalculationMode(appMode, CalculationMode.FROM_LOCATION);
			}
			elevationWidget.setElevationSmoothing(appMode, smoothing);
		}
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putBoolean(KEY_SHOW_SLOPE, showSlope);
		outState.putBoolean(KEY_SHOW_ELEVATION, showElevation);
		outState.putBoolean(KEY_SHOW_GAIN_DROP, showGainDrop);
		outState.putBoolean(KEY_SHOW_DISTANCE, showDistance);
		outState.putBoolean(KEY_TWO_LINE, twoLine);
		outState.putString(KEY_CALC_MODE, calcMode);
		outState.putBoolean(KEY_SMOOTHING, smoothing);
	}

	private interface BooleanSupplier {
		boolean get();
	}

	private interface BooleanConsumer {
		void set(boolean value);
	}
}
