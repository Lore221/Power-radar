# Power Radar Changelog

## Unreleased

### Changed
- Required Architectury, Electro Energetics, Create Big Cannons, and RPL development dependencies now resolve from reproducible Maven sources instead of local JAR files.
- RPL is now declared as a required runtime dependency, matching the mandatory CBC integration.
- Removed the unused Create Factory Logistics development runtime dependency.
- GitHub Actions now invokes the Gradle wrapper portably through Bash.
- Added automated project-structure, client-boundary, translation, and block-resource verification to the standard build.

## 0.5.1 - Analytic Linear-Drag Targeting

### Changed
- Linear-drag Target Controller aiming now finds the trajectory-height maximum and solves the low and high pitch branches independently with safeguarded Newton iterations.
- Linear-drag lifetime limits now constrain the pitch domain before root solving, preventing unreachable high arcs from entering the targeting solution.
- Shell Alarm now finds descending protected-layer crossings analytically instead of scanning every future tick.
- Interception Controllers now reuse the analytic linear-drag pitch solver and refine interception-time brackets with safeguarded secant steps.
- Shared linear-drag trajectory equations now keep targeting, threat prediction, and interception on the same CBC tick-physics contract.
- Target and Interception Controllers now cache assembled CBC muzzle geometry, reuse cached weapon kinds, and avoid physical-pitch geometry reads for non-mortar weapons.
- Radar scanning now separates acquisition from tracking: acquired entities refresh directly every scan window, projectile discovery remains fast, and regular entity discovery runs as a slower rotating overview pass.
- Multi-radar monitors now derive structure/active state across the whole network and use the first active assembled radar for primary orientation instead of blindly trusting the first controller record.
- Quadratic-drag targeting retains the previous simulated bracket-and-bisection solver.

## 0.4.4 - Experimental Radar and Interception Update

### Added
- Added the Overview Module block and 360-degree overview radar structure.
- Added Overview Module placement support, stacking up to five modules on one controller.
- Added defense threat snapshots from Shell Alarm with projectile UUID, position, velocity, ballistics, source alarm, and crossing data.
- Added analytic linear-drag threat prediction for Shell Alarm.
- Added analytic linear-drag interception search for Interception Controllers.
- Added Interception Fuze splash handling for other active threat snapshots inside the detonation cone.

### Changed
- Renamed Radar Panel to FAR Panel in English and Russian localization.
- Static radar validation now uses panels placed on the controller.
- Monitor track update timing now follows the radar scan window for FAR and overview radar modes.
- Interception Controllers now track assigned projectile UUIDs through Shell Alarm snapshots instead of depending on radar tracks.
- Interception Controller assignment now distributes controllers across active threats while prioritizing faster engagement.
- Target Controller and Interception Controller ballistic solvers now use bracketed root search for linear drag trajectories.
- Target and interception pitch solving now reuses local pitch hints during a solve and falls back to the full range when needed.
- Interception window search now uses a cheap coarse pass before running the full ballistic solver.
- Radar monitor background and FAR cone textures were updated to the new assets.

### Removed
- Removed the old improved panel implementation.
- Removed radar stand/rack implementation.
- Removed the old overview radar white scan-wave renderer.

## 0.4.0 - Alarm System Update: Mechanical Siren

### Added
- Added the Mechanical Siren kinetic block.
- Added Create shaft input on the rear side of the siren.
- Added a real mechanical siren loop derived from the provided source recording.
- Added RPM-driven sound pitch and redstone-level-driven volume.
- Added a custom olive military-industrial model and texture set.

### Balance
- Mechanical Siren stress impact is `2 SU/RPM`, reaching `512 SU` at `256 RPM`.

### Notes
- This release currently implements only the siren part of the Alarm System update.
- Alarm detection, protected radii, shell prediction, and interception are planned separately.

## 0.3.4 - Target Controller Diagnostics

### Added
- Added separate configurable minimum firing distances for autocannons and big cannons, both defaulting to 20 blocks.
- Added Target Controller Shift tooltip with resistance, working voltage, and minimum firing distances.
- Added Target Controller goggle diagnostics for voltage, current, consumption, and the current reason firing is blocked.
- Added documented, disabled-by-default bug-report debug switches.

### Changed
- Autotarget skips targets inside the configured minimum firing distance.
- Manual targets inside the minimum distance remain selected, but cannot trigger fire.
- Target System debug output is disabled by default and uses a common bug-report log prefix.

### Fixed
- Manual target selection now clears when its radar track disappears, allowing autotarget to resume.

## 0.3.2 - Whitelist Setup

### Added
- Added monitor whitelist overlay for player whitelist setup.
- Added player whitelist add/remove network payload.
- Added online-player name suggestions in the monitor whitelist overlay.
- Added network-level player whitelist storage for autotarget filtering.
- Added Sable whitelist UI placeholder column for future Sable structure support.

### Changed
- Removed target-selection filter controls from the Radar Controller GUI.
- Autotarget now ignores whitelisted players, while manual target selection can still select them.

## 0.3.1 - Autotarget

### Added
- Added monitor-side autotarget category filter.
- Added network-level autotarget filter state with all categories disabled by default.
- Added Target Controller fallback target selection from radar tracks when no valid manual monitor target is available.
- Added monitor button to clear manual target selection.
- Added monitor snapshot sync for manual target selection, so reopening the monitor restores the selected target when it is still visible in the target list.

### Changed
- Manual monitor-selected targets now keep priority over autotarget and force Target Controller target selection.
- Manual monitor-selected targets now remain locked even if unreachable; autotarget takes over only after target death/unload or manual clear.
- Autotarget now skips dead, hidden, unreachable, and out-of-ballistics targets and moves to the next matching UUID in track order.
- Target Controller now uses direct CBC mount control only and no longer exposes servo output state.

### Fixed
- Made monitor target-filter buttons functional instead of visual-only.
- Removed side horizontal/vertical servo contact markings from the Target Controller side texture.

## 0.3.0 - Target System Update

### Added
- Added Target Controller block as the first CBC targeting-system component.
- Added Create Big Cannons integration for adjustable mounts.
- Added monitor-driven manual target selection flow for the Target Controller.
- Added Target Controller CEE power input with voltage-based aiming speed.
- Added redstone fire output when the selected target is alive, visible, reachable, ammunition is available, and the cannon is settled on target.
- Added shell trajectory selection: flat trajectory and high-arc trajectory with automatic flat fallback when high arc exceeds 60 degrees.
- Added line-of-sight fire gating to prevent shooting at targets fully hidden behind blocks.
- Added CBC ballistic profile reading for big cannons and autocannons.
- Added iterative target lead based on estimated projectile flight time.

### Changed
- Target Controller now drives CBC yaw/pitch directly instead of relying on servo drives.
- CBC drag ballistic solver now follows the CBC projectile step more closely by applying `position += velocity + 0.5 * acceleration` and then `velocity += acceleration`.
- Target lead now uses solved time-of-flight instead of a simple `distance / speed` estimate.
- Shell firing readiness now depends on ammunition availability, so empty cannons do not hold a fire signal.
- Autocannon range checks now account for projectile lifetime.
- Projectiles were removed from GUI targeting-category controls for now.

### Fixed
- Fixed incorrect horizontal aiming orientation for CBC mounts.
- Fixed shell cannon redstone staying active after firing.
- Improved autocannon long-range pitch behavior by using CBC projectile speed, gravity, drag, and lifetime data.
- Improved close-range aiming origin behavior for cannons mounted above the controller.

### Known Limitations
- CBC spread is real and still causes shot-to-shot deviation even with perfect aiming.
- Dimension drag/gravity multipliers and fluid drag are not fully mirrored yet.
- Autocannon correction from already-fired projectiles is not implemented yet.
- Mob/player/Sable acceleration prediction is not implemented yet.
- Autotarget lock/search logic is planned but not implemented yet.
- Projectile alarm impact prediction is planned but not implemented yet.

### Planned
- Add smoothed acceleration per radar track for mobs, players, and future Sable structures.
- Extend autotarget with explicit lock/search tuning if target switching becomes too aggressive in testing.
- Add autocannon correction based on observed fired projectiles.
- Add shell alarm impact prediction using observed projectile position and velocity.
- Add target/alarm GUI pages and whitelist controls.
- Revisit CEE integration for future AC support and device-model cleanup.

## 0.2.4 - GUI, Config, and Optimization

### Added
- Added working config surface for radar balance values.
- Added monitor GUI hub controls for radar mode, detection filters, target filters, and shell trajectory selection.
- Added controller GUI with Create-like styling and the same core radar controls.
- Added target selection interaction on monitor blips.
- Added optimized radar display assets for GUI/in-world rendering.

### Changed
- Optimized radar scanning, stale-track handling, display updates, and network refresh behavior.
- Reworked monitor visuals toward asset-based rendering.
- Updated radar display from circular presentation to square presentation.
- Moved monitor/controller electrical diagnostics into goggle tooltip surfaces where appropriate.

### Fixed
- Fixed several GUI/in-world radar display alignment issues.
- Fixed stale debug logging and removed old disabled debug paths.
- Fixed monitor and radar link behavior after optimization passes.

## 0.2.3 - Electrical Integration, Advanced Panels, Rotary Radar

### Added
- Added CEE electrical integration for radar, monitor, rotary mount, servo drive, and target-system groundwork.
- Added Advanced Radar Panel.
- Added rotary antenna mount.
- Added negative-polarity rotary direction support.

### Changed
- Updated radar panel limit to a shared total panel limit.
- Updated range and load formulas for mixed basic/advanced panel arrays.
- Updated monitor and radar renderer alignment for rotary radar.

## 0.2.2 - Advanced Radar Panels

### Added
- Added `power_radar:advanced_radar_panel`.
- Added advanced panel assets, blockstate, model, loot table, recipe, block item, and lang keys.
- Added mixed-array support for basic and advanced radar panels.

### Changed
- Replaced basic-only panel limit naming with total radar panel limit.
- Updated radar range formula to include advanced panels.
- Updated CEE load formula to include advanced panels.
