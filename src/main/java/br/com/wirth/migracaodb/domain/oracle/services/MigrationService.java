package br.com.wirth.migracaodb.domain.oracle.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Service
public class MigrationService {

    private final JdbcTemplate oracleJdbcTemplate;
    private final JdbcTemplate postgresJdbcTemplate;

    @Autowired
    public MigrationService(JdbcTemplate oracleJdbcTemplate, JdbcTemplate postgresJdbcTemplate) {
        this.oracleJdbcTemplate = oracleJdbcTemplate;
        this.postgresJdbcTemplate = postgresJdbcTemplate;
    }

    public void migrateData(String tableName) {
        // Fetch data from Oracle
        List<Map<String, Object>> rows = oracleJdbcTemplate.queryForList("SELECT * FROM " + tableName);

        if (rows.isEmpty()) {
            return;
        }

        // Create table in PostgreSQL
        createTableInPostgres(tableName);

        // Create insert statement dynamically
        StringBuilder insertQuery = new StringBuilder("INSERT INTO " + tableName + " (");
        rows.get(0).forEach((column, value) -> insertQuery.append(column).append(","));
        insertQuery.deleteCharAt(insertQuery.length() - 1).append(") VALUES (");
        rows.get(0).forEach((column, value) -> insertQuery.append("?,"));
        insertQuery.deleteCharAt(insertQuery.length() - 1).append(")");

        // Insert data into PostgreSQL
        for (Map<String, Object> row : rows) {
            postgresJdbcTemplate.update(insertQuery.toString(), row.values().toArray());
        }

        // Migrate primary keys and foreign keys
        migratePrimaryKey(tableName);
        migrateForeignKeys(tableName);
    }

    private void createTableInPostgres(String tableName) {
        try {
            DatabaseMetaData metaData = Objects.requireNonNull(oracleJdbcTemplate.getDataSource()).getConnection().getMetaData();
            ResultSet columnsResultSet = metaData.getColumns(null, null, tableName.toUpperCase(), null);
            StringBuilder createTableQuery = new StringBuilder("CREATE TABLE " + tableName + " (");

            Set<String> addedColumns = new HashSet<>();

            ResultSet pkResultSet = metaData.getPrimaryKeys(null, null, tableName.toUpperCase());
            Set<String> primaryKeys = new HashSet<>();
            while (pkResultSet.next()) {
                primaryKeys.add(pkResultSet.getString("COLUMN_NAME"));
            }

            ResultSet fkResultSet = metaData.getImportedKeys(null, null, tableName.toUpperCase());
            Map<String, String> foreignKeys = new HashMap<>();
            while (fkResultSet.next()) {
                String fkColumnName = fkResultSet.getString("FKCOLUMN_NAME");
                String pkTableName = fkResultSet.getString("PKTABLE_NAME");
                String pkColumnName = fkResultSet.getString("PKCOLUMN_NAME");

                // Obter o tipo de dados da coluna referenciada
                ResultSet pkColumnResultSet = metaData.getColumns(null, null, pkTableName.toUpperCase(), pkColumnName.toUpperCase());
                if (pkColumnResultSet.next()) {
                    String pkColumnType = pkColumnResultSet.getString("TYPE_NAME");
                    foreignKeys.put(fkColumnName, pkColumnType);
                }
            }

            while (columnsResultSet.next()) {
                String columnName = columnsResultSet.getString("COLUMN_NAME");
                if (!addedColumns.contains(columnName)) {
                    addedColumns.add(columnName);

                    String dataType = columnsResultSet.getString("TYPE_NAME");
                    String postgresDataType;

                    if (primaryKeys.contains(columnName) && dataType.equalsIgnoreCase("NUMBER")) {
                        postgresDataType = "SERIAL";
                    } else if (foreignKeys.containsKey(columnName)) {
                        postgresDataType = "SERIAL";
                    } else{
                        postgresDataType = mapOracleToPostgresDataType(dataType);
                    }

                    createTableQuery.append(columnName).append(" ").append(postgresDataType).append(",");
                }

            }
            if (createTableQuery.charAt(createTableQuery.length() - 1) == ',') {
                // Remove a última vírgula
                createTableQuery.deleteCharAt(createTableQuery.length() - 1);
            }

            createTableQuery.append(")");

            postgresJdbcTemplate.execute(createTableQuery.toString());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private String mapOracleToPostgresDataType(String oracleDataType) {
        // Add logic to map Oracle data types to equivalent PostgreSQL data types
        // For example:
        if (oracleDataType.equalsIgnoreCase("NUMBER")) {
            return "NUMERIC";
        } else if (oracleDataType.equalsIgnoreCase("VARCHAR2")) {
            return "VARCHAR";
        } else if (oracleDataType.equalsIgnoreCase("DATE")) {
            return "TIMESTAMP";
        }
        // Add mappings for other data types as needed
        return oracleDataType;
    }
    private void migratePrimaryKey(String tableName) {
        try {
            DatabaseMetaData metaData = oracleJdbcTemplate.getDataSource().getConnection().getMetaData();
            ResultSet rs = metaData.getPrimaryKeys(null, null, tableName.toUpperCase());

            StringBuilder primaryKeyQuery = new StringBuilder("ALTER TABLE " + tableName + " ADD PRIMARY KEY (");
            while (rs.next()) {
                primaryKeyQuery.append(rs.getString("COLUMN_NAME")).append(",");
            }
            primaryKeyQuery.deleteCharAt(primaryKeyQuery.length() - 1).append(")");
            postgresJdbcTemplate.execute(primaryKeyQuery.toString());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void migrateForeignKeys(String tableName) {
        try {
            DatabaseMetaData metaData = Objects.requireNonNull(oracleJdbcTemplate.getDataSource()).getConnection().getMetaData();
            ResultSet rs = metaData.getImportedKeys(null, null, tableName.toUpperCase());

            while (rs.next()) {
                String fkName = rs.getString("FK_NAME");
                String pkTableName = rs.getString("PKTABLE_NAME");
                String pkColumnName = rs.getString("PKCOLUMN_NAME");
                String fkColumnName = rs.getString("FKCOLUMN_NAME");

                String foreignKeyQuery = "ALTER TABLE " + tableName + " ADD CONSTRAINT " + fkName +
                        " FOREIGN KEY (" + fkColumnName + ") REFERENCES " + pkTableName + " (" + pkColumnName + ")";
                postgresJdbcTemplate.execute(foreignKeyQuery);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
