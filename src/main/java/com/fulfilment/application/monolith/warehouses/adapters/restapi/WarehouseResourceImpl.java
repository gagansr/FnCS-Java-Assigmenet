
package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import com.fulfilment.application.monolith.warehouses.adapters.database.WarehouseRepository;
import com.fulfilment.application.monolith.warehouses.domain.ports.ArchiveWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.CreateWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.ReplaceWarehouseOperation;
import com.warehouse.api.WarehouseResource;
import com.warehouse.api.beans.Warehouse;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.WebApplicationException;
import java.util.List;
import java.util.Objects;

@RequestScoped
public class WarehouseResourceImpl implements WarehouseResource {

  @Inject
  WarehouseRepository warehouseRepository;

  // Use cases keep business rules out of the REST resource.
  @Inject
  CreateWarehouseOperation createWarehouseOperation;

  @Inject
  ReplaceWarehouseOperation replaceWarehouseOperation;

  @Inject
  ArchiveWarehouseOperation archiveWarehouseOperation;


@Override
public List<Warehouse> listAllWarehousesUnits() {
  // Return only active warehouses; archived records remain in the database for history.
  return warehouseRepository.getAll().stream()
      .filter(warehouse -> warehouse.archivedAt == null)
      .map(this::toWarehouseResponse)
      .toList();
}


  @Override
  public Warehouse createANewWarehouseUnit(@NotNull Warehouse data) {
    // Reject an absent request body before accessing its fields.
    if (data == null) {
      throw new WebApplicationException(
          "Warehouse request is required.", 400);
    }

    // Convert the incoming API object into the domain model.
    var warehouse = toDomainWarehouse(data);

    // The use case validates the request before persisting the warehouse.
    createWarehouseOperation.create(warehouse);

    // The repository copies the generated database ID back to this object.
    return toWarehouseResponse(warehouse);
  }

  @Override
  public Warehouse getAWarehouseUnitByID(String id) {
    // Find the requested record by its database-generated ID.
    var warehouse = findByDatabaseId(id);

    if (warehouse == null) {
      throw new WebApplicationException(
          "Warehouse with ID " + id + " does not exist.", 404);
    }

    // Return the record, including its ID in the API response.
    return toWarehouseResponse(warehouse);
  }

  @Override
  public void archiveAWarehouseUnitByID(String id) {
    // Resolve the database ID before invoking the archive use case.
    var warehouse = findByDatabaseId(id);

    // An already archived or unknown warehouse cannot be archived again.
    if (warehouse == null || warehouse.archivedAt != null) {
      throw new WebApplicationException(
          "Active warehouse with ID " + id + " does not exist.", 404);
    }

    // The use case marks the record archived instead of physically deleting it.
    archiveWarehouseOperation.archive(warehouse);
  }

  @Override
  public Warehouse replaceTheCurrentActiveWarehouse(
      String businessUnitCode, @NotNull Warehouse data) {

    // Reject an absent replacement request.
    if (data == null) {
      throw new WebApplicationException(
          "Replacement request is required.", 400);
    }

    // If the request supplies a code, it must match the path identifier.
    if (data.getBusinessUnitCode() != null
        && !Objects.equals(
            businessUnitCode, data.getBusinessUnitCode())) {
      throw new WebApplicationException(
          "Business unit code must match the path.", 400);
    }

    // Convert the API request to the domain model.
    var replacement = toDomainWarehouse(data);

    // The path identifies the business unit being replaced.
    replacement.businessUnitCode = businessUnitCode;

    // The use case validates, archives the previous record, and creates its replacement.
    replaceWarehouseOperation.replace(replacement);

    // Return the new record with its newly generated database ID.
    return toWarehouseResponse(replacement);
  }

  private com.fulfilment.application.monolith.warehouses.domain.models.Warehouse
      findByDatabaseId(String id) {

    // Invalid or absent IDs do not identify a database record.
    if (id == null || id.isBlank()) {
      return null;
    }

    // Compare the API's string ID against the domain model's database ID.
    return warehouseRepository.getAll().stream()
        .filter(warehouse ->
            warehouse.id != null
                && warehouse.id.toString().equals(id))
        .findFirst()
        .orElse(null);
  }

  private com.fulfilment.application.monolith.warehouses.domain.models.Warehouse
      toDomainWarehouse(Warehouse data) {

    // Keep the API DTO separate from the internal domain model.
    var warehouse =
        new com.fulfilment.application.monolith.warehouses.domain.models.Warehouse();

    warehouse.businessUnitCode = data.getBusinessUnitCode();
    warehouse.location = data.getLocation();
    warehouse.capacity = data.getCapacity();
    warehouse.stock = data.getStock();

    return warehouse;
  }

  private Warehouse toWarehouseResponse(
      com.fulfilment.application.monolith.warehouses.domain.models.Warehouse warehouse) {

    var response = new Warehouse();

    // The OpenAPI schema represents the database ID as a string.
    response.setId(
        warehouse.id == null ? null : warehouse.id.toString());

    response.setBusinessUnitCode(warehouse.businessUnitCode);
    response.setLocation(warehouse.location);
    response.setCapacity(warehouse.capacity);
    response.setStock(warehouse.stock);

    return response;
  }
}
