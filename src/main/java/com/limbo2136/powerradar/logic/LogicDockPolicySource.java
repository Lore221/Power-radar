package com.limbo2136.powerradar.logic;

import java.util.List;
import java.util.UUID;

/** Общий источник карточной политики для обычного и панельного Logic Dock. */
public interface LogicDockPolicySource {
    boolean isElectricallyOperational();

    int targetingMask();

    int displayMask();

    boolean allowlistIsWhitelist();

    List<String> allowlistPlayerNames();

    List<String> allowlistSableNames();

    List<String> allowlistedPlayers();

    List<String> allowlistedSableNames();

    /**
     * Панельные источники подтверждают свою runtime-регистрацию через этот метод.
     * Обычный блок обнаруживается напрямую через загруженный Radar Link.
     */
    default boolean isAvailableForNetwork(UUID networkId) {
        return true;
    }
}
