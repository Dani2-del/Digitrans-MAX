package com.transcaribe.transcaribe.Util;

import com.transcaribe.transcaribe.Model.SistemaTransporte;

public final class TarifaTranscaribe {

    public static final long VALOR_PASAJE_TRANSCARIBE_COP = 3900L;
    public static final long VALOR_PASAJE_TRANSMILENIO_COP = 3550L;

    public static long paraSistema(SistemaTransporte sistema) {
        return switch (sistema) {
            case TRANSCARIBE -> VALOR_PASAJE_TRANSCARIBE_COP;
            case TRANSMILENIO -> VALOR_PASAJE_TRANSMILENIO_COP;
            case TRANSMETRO -> throw new IllegalArgumentException(
                    "Las recargas de Transmetro todavía no están habilitadas.");
        };
    }

    private TarifaTranscaribe() {
    }
}
