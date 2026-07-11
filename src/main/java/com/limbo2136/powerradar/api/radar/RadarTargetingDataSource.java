package com.limbo2136.powerradar.api.radar;

import com.limbo2136.powerradar.radar.TargetTrajectoryMode;

public interface RadarTargetingDataSource extends RadarDataSource {
    TargetTrajectoryMode targetTrajectoryMode();
}
