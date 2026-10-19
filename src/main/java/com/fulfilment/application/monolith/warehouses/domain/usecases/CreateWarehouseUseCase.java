
package com.fulfilment.application.monolith.warehouses.domain.usecases;

import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.CreateWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.LocationResolver;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;

@ApplicationScoped
public class CreateWarehouseUseCase implements CreateWarehouseOperation {

  private final WarehouseStore warehouseStore;
  private final LocationResolver locationResolver;

  public CreateWarehouseUseCase(
      WarehouseStore warehouseStore,
      LocationResolver locationResolver) {
    this.warehouseStore = warehouseStore;
    this.locationResolver = locationResolver;
  }

  @Override
  @Transactional
  public void create(Warehouse warehouse) {

    // Reject an incomplete request before performing any persistence.
    if (warehouse == null
        || warehouse.businessUnitCode == null
        || warehouse.location == null
        || warehouse.capacity == null
        || warehouse.stock == null
        || warehouse.businessUnitCode.isBlank()
        || warehouse.location.isBlank()
        || warehouse.capacity < 0
        || warehouse.stock < 0) {
      throw new WebApplicationException(
          "Required warehouse fields must be provided.", 422);
    }

    // Prevent duplicate business-unit codes among active warehouses.
    if (warehouseStore.findByBusinessUnitCode(warehouse.businessUnitCode) != null) {
      throw new WebApplicationException(
          "An active warehouse with this business unit code already exists.", 409);
    }

    // Resolve the location to verify it exists and obtain its limits.
    Location location = locationResolver.resolveByIdentifier(warehouse.location);

    if (location == null) {
      throw new WebApplicationException(
          "The specified warehouse location does not exist.", 422);
    }

    // Only active warehouses count toward the location's warehouse limit.
    var activeWarehousesAtLocation = warehouseStore.getAll().stream()
        .filter(existing -> existing.archivedAt == null)
        .filter(existing -> location.identification.equals(existing.location))
        .toList();

    if (activeWarehousesAtLocation.size() >= location.maxNumberOfWarehouses) {
      throw new WebApplicationException(
          "The maximum number of warehouses for this location has been reached.", 422);
    }

    // A warehouse cannot have more capacity than the location allows.
    if (warehouse.capacity > location.maxCapacity) {
      throw new WebApplicationException(
          "Warehouse capacity exceeds the location's maximum capacity.", 422);
    }

    // Capacity is shared across all active warehouses at the same location.
    int existingCapacity = activeWarehousesAtLocation.stream()
        .mapToInt(existing -> existing.capacity == null ? 0 : existing.capacity)
        .sum();

    if (existingCapacity + warehouse.capacity > location.maxCapacity) {
      throw new WebApplicationException(
          "Total warehouse capacity would exceed the location's maximum capacity.", 422);
    }

    // Ensure the warehouse has enough capacity for its declared stock.
    if (warehouse.stock > warehouse.capacity) {
      throw new WebApplicationException(
          "Warehouse stock cannot exceed its capacity.", 422);
    }

    warehouse.createdAt = LocalDateTime.now();
    warehouse.archivedAt = null;

    // Persist only after all required business validations succeed.
    warehouseStore.create(warehouse);
  }
}
