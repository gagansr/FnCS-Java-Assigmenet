package com.fulfilment.application.monolith.stores;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.List;
import org.jboss.logging.Logger;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;

@Path("store")
@ApplicationScoped
@Produces("application/json")
@Consumes("application/json")
public class StoreResource {

  @Inject LegacyStoreManagerGateway legacyStoreManagerGateway;

  @Inject
  TransactionSynchronizationRegistry transactionSynchronizationRegistry;

  private static final Logger LOGGER = Logger.getLogger(StoreResource.class.getName());


/**
 * Executes an action only after the current database transaction
 * successfully commits.
 *
 * Why is this needed?
 * We don't want to update the legacy system if our database
 * transaction rolls back.
 *
 * @param action the operation to execute after a successful commit
 */
private void executeAfterCommit(Runnable action) {

    // Register a callback with the current JTA transaction.
    // The transaction manager will invoke this callback
    // when the transaction completes.
    transactionSynchronizationRegistry
            .registerInterposedSynchronization(
                    new Synchronization() {

                        @Override
                        public void beforeCompletion() {
                            // The transaction has not completed yet.
                            // Do not call the legacy system here,
                            // because the database commit is not confirmed.
                        }

                        @Override
                        public void afterCompletion(int status) {

                            // Execute the legacy operation ONLY if
                            // the database transaction committed successfully.
                            if (status == Status.STATUS_COMMITTED) {
                                try {

                                    // Execute the operation supplied by
                                    // the calling method, such as create
                                    // or update in the legacy system.
                                    action.run();

                                } catch (RuntimeException exception) {

                                    // The database has already committed.
                                    // We cannot roll it back because the
                                    // legacy operation failed.
                                    // Log the failure for investigation.
                                    LOGGER.error(
                                            "Store committed, but legacy synchronization failed",
                                            exception
                                    );
                                }
                            }

                            // If the transaction rolled back,
                            // the condition above is false.
                            // Therefore, the legacy operation is skipped.
                        }
                    }
            );
}

/**
 * Creates a separate Store object containing the fields required
 * by the legacy-system integration.
 *
 * Why is this needed?
 * The legacy callback executes after transaction completion.
 * We don't want the callback to depend on the lifecycle of the
 * original Hibernate-managed entity.
 *
 * This copy is used for legacy synchronization only.
 * It is NOT persisted as another database record.
 *
 * @param source the original Store entity
 * @return a separate Store object with the required field values
 */
  private Store snapshot(Store source) {

      // Create a new Java object using the source Store's name.
      // This does not insert another record into the database.
      Store copy = new Store(source.name);

      // Copy the existing Store ID.
      // This preserves the identity of the Store being synchronized.
      copy.id = source.id;

      // Copy the current stock quantity.
      // The legacy gateway receives the stock value captured here.
      copy.quantityProductsInStock =
              source.quantityProductsInStock;

      // Return the independent object for use in the callback.
      return copy;
  }

  @GET
  public List<Store> get() {
    return Store.listAll(Sort.by("name"));
  }

  @GET
  @Path("{id}")
  public Store getSingle(Long id) {
    Store entity = Store.findById(id);
    if (entity == null) {
      throw new WebApplicationException("Store with id of " + id + " does not exist.", 404);
    }
    return entity;
  }

  @POST
  @Transactional
  public Response create(Store store) {
    if (store.id != null) {
      throw new WebApplicationException("Id was invalidly set on request.", 422);
    }

    store.persist();

    // Capture the Store fields needed by the legacy system.
    // The callback will use this copy after transaction completion.
    Store storeForLegacySync = snapshot(store);

    // Register the legacy creation operation.
    // This operation will run only after a successful commit.
    executeAfterCommit(
            () -> legacyStoreManagerGateway
                    .createStoreOnLegacySystem(storeForLegacySync)
    );


    return Response.ok(store).status(201).build();
  }

  @PUT
  @Path("{id}")
  @Transactional
  public Store update(Long id, Store updatedStore) {
    if (updatedStore.name == null) {
      throw new WebApplicationException("Store Name was not set on request.", 422);
    }

    Store entity = Store.findById(id);

    if (entity == null) {
      throw new WebApplicationException("Store with id of " + id + " does not exist.", 404);
    }

    entity.name = updatedStore.name;
    entity.quantityProductsInStock = updatedStore.quantityProductsInStock;

       // Create an independent copy containing the updated values.
    // The legacy callback will use this snapshot after transaction
    // completion instead of depending on the original managed entity.
    Store storeForLegacySync = snapshot(entity);

    // Register the legacy update for after transaction completion.
    // The legacy gateway will execute only if the transaction commits.
    // If the transaction rolls back, this operation will be skipped.
    executeAfterCommit(
            () -> legacyStoreManagerGateway
                    .updateStoreOnLegacySystem(storeForLegacySync)
    );

    return entity;
  }

  @PATCH
  @Path("{id}")
  @Transactional
  public Store patch(Long id, Store updatedStore) {
    if (updatedStore.name == null) {
      throw new WebApplicationException("Store Name was not set on request.", 422);
    }

    Store entity = Store.findById(id);

    if (entity == null) {
      throw new WebApplicationException("Store with id of " + id + " does not exist.", 404);
    }

    if (entity.name != null) {
      entity.name = updatedStore.name;
    }

    if (entity.quantityProductsInStock != 0) {
      entity.quantityProductsInStock = updatedStore.quantityProductsInStock;
    }
    // Capture the resulting Store state for the legacy integration.
    // The callback will not depend on the original managed entity's lifecycle.
    Store storeForLegacySync = snapshot(entity);

    // Register the legacy update for after transaction completion.
    // The legacy gateway runs only if the transaction commits successfully.
    // If the transaction rolls back, the gateway is not called.
    executeAfterCommit(
            () -> legacyStoreManagerGateway
                    .updateStoreOnLegacySystem(storeForLegacySync)
    );

    return entity;
  }

  @DELETE
  @Path("{id}")
  @Transactional
  public Response delete(Long id) {
    Store entity = Store.findById(id);
    if (entity == null) {
      throw new WebApplicationException("Store with id of " + id + " does not exist.", 404);
    }
    entity.delete();
    return Response.status(204).build();
  }


  @Provider
  public static class ErrorMapper implements ExceptionMapper<Exception> {

    @Inject ObjectMapper objectMapper;

    @Override
    public Response toResponse(Exception exception) {
      LOGGER.error("Failed to handle request", exception);

      int code = 500;
      if (exception instanceof WebApplicationException) {
        code = ((WebApplicationException) exception).getResponse().getStatus();
      }

      ObjectNode exceptionJson = objectMapper.createObjectNode();
      exceptionJson.put("exceptionType", exception.getClass().getName());
      exceptionJson.put("code", code);

      if (exception.getMessage() != null) {
        exceptionJson.put("error", exception.getMessage());
      }

      return Response.status(code).entity(exceptionJson).build();
    }
  }
}
