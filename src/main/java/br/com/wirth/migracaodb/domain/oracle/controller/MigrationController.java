package br.com.wirth.migracaodb.domain.oracle.controller;

import br.com.wirth.migracaodb.domain.oracle.services.MigrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/migrate")
public class MigrationController {

    private final MigrationService migrationService;

    @Autowired
    public MigrationController(MigrationService migrationService) {
        this.migrationService = migrationService;
    }

    @PostMapping("/{tableName}")
    public ResponseEntity<String> migrate(@PathVariable String tableName) {
        migrationService.migrateData(tableName);
        return ResponseEntity.ok("Migration of table " + tableName + " completed successfully!");
    }
}

