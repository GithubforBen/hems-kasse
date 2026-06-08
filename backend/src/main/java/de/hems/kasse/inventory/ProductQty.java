package de.hems.kasse.inventory;

import java.util.UUID;

/** Per-product summed-quantity projection, shared by the Lager consumption/intake aggregation queries. */
public interface ProductQty {
    UUID getProductId();
    long getQty();
}
