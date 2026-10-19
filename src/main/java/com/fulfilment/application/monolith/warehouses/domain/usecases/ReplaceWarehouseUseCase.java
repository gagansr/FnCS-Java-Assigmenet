
package com.fulfilment.application.monolith.warehouses.domain.usecases;

import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.LocationResolver;
import com.fulfilment.application.monolith.warehouses.domain.ports.ReplaceWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import java.time.LocalDateTime;
import java.util.Objects;

@ApplicationScoped
public class ReplaceWarehouseUseCase implements ReplaceWarehouseOperation {

  private final WarehouseStore warehouseStore;
  private final LocationResolver locationResolver;

  public ReplaceWarehouseUseCase(
      WarehouseStore warehouseStore,
      LocationResolver locationResolver) {
    this.warehouseStore = warehouseStore;
    this.locationResolver = locationResolver;
  }

  @Override
  @Transactional
  public void replace(Warehouse newWarehouse) {

    // Reject incomplete replacement requests before accessing the database.
    if (newWarehouse == null
    || newWarehouse.businessUnitCode == null
    || newWarehouse.location == null
    || newWarehouse.capacity == null
    || newWarehouse.stock == null
    || newWarehouse.businessUnitCode.isBlank()
    || newWarehouse.location.isBlank()
    || newWarehouse.capacity < 0
    || newWarehouse.stock < 0) {
  throw new WebApplicationException(
      "Required warehouse fields must be provided.", 400);
}

    // A replacement requires an existing active warehouse with this code.
    Warehouse currentWarehouse =
        warehouseStore.findByBusinessUnitCode(newWarehouse.businessUnitCode);

    if (currentWarehouse == null) {
      throw new WebApplicationException(
          "Active warehouse not found: " + newWarehouse.businessUnitCode, 404);
    }

    // The replacement must preserve the existing warehouse's location.
    if (!Objects.equals(newWarehouse.location, currentWarehouse.location)) {
      throw new WebApplicationException(
          "Replacement warehouse must use the same location.", 400);
    }

    // Verify that the replacement location is a recognized location.
    Location location = locationResolver.resolveByIdentifier(newWarehouse.location);

    if (location == null) {
      throw new WebApplicationException(
          "The specified warehouse location does not exist.", 400);
    }

    // Preserve the existing warehouse's stock during replacement.
    if (!Objects.equals(newWarehouse.stock, currentWarehouse.stock)) {
      throw new WebApplicationException(
          "Replacement warehouse stock must match the existing warehouse stock.", 400);
    }

    // The new warehouse must have capacity for all existing stock.
    if (newWarehouse.capacity < currentWarehouse.stock
        || newWarehouse.capacity > location.maxCapacity) {
      throw new WebApplicationException(
          "Replacement warehouse capacity cannot accommodate the stock or exceeds the location limit.",
          400);
    }

    // Exclude the warehouse being replaced when checking active location limits.
    var otherActiveWarehouses = warehouseStore.getAll().stream()
        .filter(warehouse -> warehouse.archivedAt == null)
        .filter(warehouse ->
            Objects.equals(warehouse.location, newWarehouse.location))
        .filter(warehouse ->
            !Objects.equals(
                warehouse.businessUnitCode,
                currentWarehouse.businessUnitCode))
        .toList();

    // The new warehouse will take the place of the old one.
    if (otherActiveWarehouses.size() + 1 > location.maxNumberOfWarehouses) {
      throw new WebApplicationException(
          "The location's maximum number of warehouses would be exceeded.", 400);
    }

    // Check total capacity without counting the old warehouse's capacity.
    int otherWarehouseCapacity = otherActiveWarehouses.stream()
        .mapToInt(warehouse -> warehouse.capacity == null ? 0 : warehouse.capacity)
        .sum();

    if (otherWarehouseCapacity + newWarehouse.capacity > location.maxCapacity) {
      throw new WebApplicationException(
          "Total warehouse capacity would exceed the location's maximum capacity.",
          400);
    }

    // Use one timestamp for the archive and replacement creation.
    LocalDateTime replacementTime = LocalDateTime.now();

    // Archive the old record instead of deleting its history.
    currentWarehouse.archivedAt = replacementTime;
    warehouseStore.update(currentWarehouse);

    // Create the replacement as a new active record with the same business code.
    newWarehouse.createdAt = replacementTime;
    newWarehouse.archivedAt = null;
    warehouseStore.create(newWarehouse);
  }
}
