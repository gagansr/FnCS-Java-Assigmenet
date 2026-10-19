
package com.fulfilment.application.monolith.warehouses.domain.usecases;

import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.ArchiveWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import java.time.LocalDateTime;

@ApplicationScoped
public class ArchiveWarehouseUseCase implements ArchiveWarehouseOperation {

  private final WarehouseStore warehouseStore;

  public ArchiveWarehouseUseCase(WarehouseStore warehouseStore) {
    this.warehouseStore = warehouseStore;
  }

  @Override
  @Transactional
  public void archive(Warehouse warehouse) {

    // Validate the identifier before looking up the warehouse.
    if (warehouse == null
        || warehouse.businessUnitCode == null
        || warehouse.businessUnitCode.isBlank()) {
      throw new WebApplicationException(
          "A valid business unit code is required.", 400);
    }

    // Only an existing active warehouse can be archived.
    Warehouse existing =
        warehouseStore.findByBusinessUnitCode(warehouse.businessUnitCode);

    if (existing == null) {
      throw new WebApplicationException(
          "Active warehouse not found: " + warehouse.businessUnitCode, 404);
    }

    // Mark the record as archived instead of physically deleting it.
    existing.archivedAt = LocalDateTime.now();

    // Persist the archive timestamp in the current transaction.
    warehouseStore.update(existing);
  }
}
