package com.nibras.pim.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A displayable app module (one per entity slug). Lets the user enable/disable it in the
 * nav and assign a short {@code code} (e.g. "T") for command-style interaction.
 * Mapped to the additive {@code app_module} table (see db/extra-schema.sql).
 */
@Entity
@Table(name = "app_module")
@Getter
@Setter
public class AppModule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "module_key", nullable = false, unique = true)
    private String moduleKey;

    @Column(nullable = false)
    private String label;

    private String code;

    /** Accent colour for the module (hex, e.g. {@code #7cb342}); shown as the row bar in lists. */
    private String color;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /** Record folder nests under an id/100 bucket: {@code <code>/<id/100>/<id>}. */
    @Column(name = "bucket_by_100", nullable = false)
    private Boolean bucketBy100 = false;

    /** Record folder nests under its type code: {@code <code>/<type>/…/<id>}. */
    @Column(name = "bucket_by_type", nullable = false)
    private Boolean bucketByType = false;
}
