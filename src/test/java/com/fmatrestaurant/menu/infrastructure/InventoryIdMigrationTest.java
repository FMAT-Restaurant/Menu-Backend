package com.fmatrestaurant.menu.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;

import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Verifies the upgrade from existing UUID columns without touching a deployed database. */
class InventoryIdMigrationTest {

	@Test
	void convertsExistingReferencesAndAcceptsOpaqueIds() throws Exception {
		try (var postgres = new PostgreSQLContainer("postgres:18.6")) {
			postgres.start();
			try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(),
					postgres.getUsername(), postgres.getPassword()); var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE component_ingredient (inventory_item_id uuid NOT NULL)");
				statement.execute("CREATE TABLE slot_option (inventory_item_id uuid)");
				String oldId = "5c67b890-1a2b-4d3e-8f9a-0b1c2d3e4f5a";
				statement.execute("INSERT INTO component_ingredient VALUES ('" + oldId + "')");
				statement.execute("INSERT INTO slot_option VALUES ('" + oldId + "'), (NULL)");
				statement.execute(Files.readString(Path.of("docs/sql/inventory-item-id-to-text.sql")));
				for (String table : new String[] { "component_ingredient", "slot_option" }) {
					try (var result = statement.executeQuery("SELECT inventory_item_id FROM " + table
							+ " WHERE inventory_item_id IS NOT NULL")) {
						assertTrue(result.next());
						assertEquals(oldId, result.getString(1));
					}
					String opaqueId = "000042:" + "x".repeat(300);
					statement.execute("INSERT INTO " + table + " VALUES ('" + opaqueId + "')");
					try (var result = statement.executeQuery("SELECT inventory_item_id FROM " + table
							+ " WHERE inventory_item_id = '" + opaqueId + "'")) {
						assertTrue(result.next());
						assertEquals(opaqueId, result.getString(1));
					}
				}
				try (var result = statement.executeQuery("SELECT count(*) FROM slot_option WHERE inventory_item_id IS NULL")) {
					assertTrue(result.next());
					assertEquals(1, result.getInt(1));
				}
			}
		}
	}

}
