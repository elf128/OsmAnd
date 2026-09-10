package net.osmand.plus.charts;

import androidx.annotation.Nullable;

import com.github.mikephil.charting.highlight.Highlight;

public class GPXHighlight extends Highlight {

	private final boolean showIcon;
	private final boolean isWaypoint;
	private final boolean isDestination;

	private double gainM = Double.NaN;
	private double dropM = Double.NaN;
	private boolean isUnavailable = false;
	@Nullable
	private Boolean isAhead = null;
	private double distanceM = Double.NaN;

	public GPXHighlight(float x, int dataSetIndex, boolean showIcon) {
		super(x, Float.NaN, dataSetIndex);
		this.showIcon = showIcon;
		this.isWaypoint = false;
		this.isDestination = false;
	}

	public GPXHighlight(float x, int dataSetIndex, boolean showIcon, boolean isWaypoint) {
		super(x, Float.NaN, dataSetIndex);
		this.showIcon = showIcon;
		this.isWaypoint = isWaypoint;
		this.isDestination = false;
	}

	public GPXHighlight(float x, int dataSetIndex, boolean showIcon, boolean isWaypoint, boolean isDestination) {
		super(x, Float.NaN, dataSetIndex);
		this.showIcon = showIcon;
		this.isWaypoint = isWaypoint;
		this.isDestination = isDestination;
	}

	public boolean shouldShowLocationIcon() {
		return showIcon;
	}

	public boolean isWaypoint() {
		return isWaypoint;
	}

	public boolean isDestination() {
		return isDestination;
	}

	public double getGainM() {
		return gainM;
	}

	public double getDropM() {
		return dropM;
	}

	public boolean isUnavailable() {
		return isUnavailable;
	}

	@Nullable
	public Boolean getIsAhead() {
		return isAhead;
	}

	public double getDistanceM() {
		return distanceM;
	}

	public void setDiffs(double gainM, double dropM) {
		this.gainM = gainM;
		this.dropM = dropM;
		this.isUnavailable = false;
	}

	public void setUnavailable() {
		this.gainM = 0;
		this.dropM = 0;
		this.isUnavailable = true;
		this.isAhead = null;
		this.distanceM = Double.NaN;
	}

	public void setIsAhead(@Nullable Boolean isAhead) {
		this.isAhead = isAhead;
	}

	public void setDistanceM(double distanceM) {
		this.distanceM = distanceM;
	}
}
