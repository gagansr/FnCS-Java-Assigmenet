
package com.fulfilment.application.monolith.warehouses.adapters.database;

import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class WarehouseRepository
    implements WarehouseStore, PanacheRepository<DbWarehouse> {

  @Override
  public List<Warehouse> getAll() {
    // Convert database entities into domain models before returning them.
    return this.listAll().stream()
        .map(DbWarehouse::toWarehouse)
        .toList();
  }

 
@Override
public void create(Warehouse warehouse) {
  // Map the domain model to the database entity.
  DbWarehouse entity = new DbWarehouse();
  entity.businessUnitCode = warehouse.businessUnitCode;
  entity.location = warehouse.location;
  entity.capacity = warehouse.capacity;
  entity.stock = warehouse.stock;
  entity.createdAt = warehouse.createdAt;
  entity.archivedAt = warehouse.archivedAt;

  // Persist first so Hibernate can generate the database ID.
  this.persist(entity);

  // Copy the generated ID back to the domain model after persistence.
  warehouse.id = entity.id;
}


  @Override
  public void update(Warehouse warehouse) {
    // Find the active record so an archived historical record is not updated.
    DbWarehouse entity = find(
        "businessUnitCode = ?1 and archivedAt is null",
        warehouse.businessUnitCode
    ).firstResult();

    if (entity == null) {
      // Fail explicitly rather than silently ignoring a missing warehouse.
      throw new IllegalArgumentException(
          "Active warehouse not found: " + warehouse.businessUnitCode);
    }

    // Copy the requested state into the existing managed entity.
    entity.location = warehouse.location;
    entity.capacity = warehouse.capacity;
    entity.stock = warehouse.stock;
    entity.createdAt = warehouse.createdAt;
    entity.archivedAt = warehouse.archivedAt;
  }

  @Override
  public void remove(Warehouse warehouse) {
    // Locate the active record before deleting it.
    DbWarehouse entity = find(
        "businessUnitCode = ?1 and archivedAt is null",
        warehouse.businessUnitCode
    ).firstResult();

    if (entity != null) {
      // Delete is separate from archive; archive should use update().
      this.delete(entity);
    }
  }

  @Override
  public Warehouse findByBusinessUnitCode(String buCode) {
    // Return only an active warehouse; archived records are history.
    DbWarehouse entity = find(
        "businessUnitCode = ?1 and archivedAt is null",
        buCode
    ).firstResult();

    return entity == null ? null : entity.toWarehouse();
  }
}
