package de.hems.kasse.catalog;

import de.hems.kasse.export.CsvWriter;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static de.hems.kasse.export.CsvWriter.euro;
import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api")
public class CatalogController {

    private final CategoryRepository categories;
    private final ProductRepository products;
    private final ProductComponentRepository components;

    public CatalogController(CategoryRepository categories, ProductRepository products, ProductComponentRepository components) {
        this.categories = categories;
        this.products = products;
        this.components = components;
    }

    // ---------- DTOs ----------
    public record ComponentDto(UUID productId, String name, int qty) {
        static ComponentDto of(ProductComponent pc) {
            return new ComponentDto(pc.getComponentProduct().getId(), pc.getComponentProduct().getName(), pc.getQty());
        }
    }
    public record ProductDto(UUID id, String name, int priceCents, String color, int sortOrder, boolean variable,
                             String plu, boolean composed, List<ComponentDto> components) {
        static ProductDto of(Product p) {
            var comps = p.getComponents().stream()
                    .sorted(Comparator.comparing(pc -> pc.getComponentProduct().getName()))
                    .map(ComponentDto::of).toList();
            return new ProductDto(p.getId(), p.getName(), p.getPriceCents(), p.getColor(), p.getSortOrder(), p.isVariable(),
                    p.getPlu(), p.isComposed(), comps);
        }
    }
    public record CategoryDto(UUID id, String name, String color, int sortOrder, List<ProductDto> products) {
        static CategoryDto of(Category c) {
            var list = c.getProducts().stream()
                    .sorted(Comparator.comparingInt(Product::getSortOrder))
                    .map(ProductDto::of).toList();
            return new CategoryDto(c.getId(), c.getName(), c.getColor(), c.getSortOrder(), list);
        }
    }

    public record NewCategory(@NotBlank @Size(max = 80) String name,
                              @NotBlank @Size(max = 20) String color) {}
    public record PatchCategory(@Size(max = 80) String name,
                                @Size(max = 20) String color,
                                Integer sortOrder) {}

    public record NewProduct(@NotBlank @Size(max = 120) String name,
                             @Min(0) int priceCents,
                             @NotBlank @Size(max = 20) String color,
                             boolean variable,
                             @Size(max = 40) String plu) {}
    public record PatchProduct(@Size(max = 120) String name,
                               Integer priceCents,
                               @Size(max = 20) String color,
                               Integer sortOrder,
                               UUID categoryId,
                               Boolean variable,
                               @Size(max = 40) String plu) {}

    public record NewComponent(@NotNull UUID componentProductId, @Min(1) int qty) {}
    public record SetComponents(@NotNull List<@Valid NewComponent> components) {}

    // ---------- Reads ----------
    @GetMapping("/categories")
    @Transactional
    public List<CategoryDto> list() {
        return categories.findAllByOrderBySortOrderAsc().stream().map(CategoryDto::of).toList();
    }

    // ---------- Categories ----------
    @PostMapping("/categories")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public CategoryDto create(@RequestBody @Valid NewCategory body) {
        int nextOrder = categories.findAll().stream()
                .mapToInt(Category::getSortOrder).max().orElse(0) + 1;
        Category c = Category.builder()
                .id(UUID.randomUUID())
                .name(body.name().trim())
                .color(body.color())
                .sortOrder(nextOrder)
                .build();
        return CategoryDto.of(categories.save(c));
    }

    @PatchMapping("/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public CategoryDto patchCategory(@PathVariable UUID id, @RequestBody @Valid PatchCategory body) {
        Category c = categories.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        if (body.name() != null && !body.name().isBlank()) c.setName(body.name().trim());
        if (body.color() != null && !body.color().isBlank()) c.setColor(body.color());
        if (body.sortOrder() != null) c.setSortOrder(body.sortOrder());
        return CategoryDto.of(categories.save(c));
    }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void deleteCategory(@PathVariable UUID id) {
        Category c = categories.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        List<UUID> productIds = c.getProducts().stream().map(Product::getId).toList();
        if (!productIds.isEmpty()) {
            components.deleteByProductIds(productIds);
        }
        categories.deleteById(id);
    }

    // ---------- Products ----------
    @PostMapping("/categories/{catId}/products")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ProductDto addProduct(@PathVariable UUID catId, @RequestBody @Valid NewProduct body) {
        Category c = categories.findById(catId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        int nextOrder = c.getProducts().stream().mapToInt(Product::getSortOrder).max().orElse(0) + 1;
        Product p = Product.builder()
                .id(UUID.randomUUID())
                .category(c)
                .name(body.name().trim())
                .priceCents(body.priceCents())
                .color(body.color())
                .sortOrder(nextOrder)
                .variable(body.variable())
                .plu(normalisePlu(body.plu()))
                .build();
        return ProductDto.of(saveProduct(p));
    }

    @PatchMapping("/products/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ProductDto patchProduct(@PathVariable UUID id, @RequestBody @Valid PatchProduct body) {
        Product p = products.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        if (body.name() != null && !body.name().isBlank()) p.setName(body.name().trim());
        if (body.priceCents() != null && body.priceCents() >= 0) p.setPriceCents(body.priceCents());
        if (body.color() != null && !body.color().isBlank()) p.setColor(body.color());
        if (body.sortOrder() != null) p.setSortOrder(body.sortOrder());
        if (body.categoryId() != null) {
            Category newCat = categories.findById(body.categoryId())
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Zielkategorie nicht gefunden"));
            p.setCategory(newCat);
        }
        if (body.variable() != null) p.setVariable(body.variable());
        if (body.plu() != null) p.setPlu(normalisePlu(body.plu()));
        return ProductDto.of(saveProduct(p));
    }

    @DeleteMapping("/products/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void deleteProduct(@PathVariable UUID id) {
        if (!products.existsById(id)) throw new ResponseStatusException(NOT_FOUND);
        try {
            products.deleteById(id);
            products.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(CONFLICT, "Produkt wird in Verkaufstasten verwendet");
        }
    }

    // ---------- Komposition (Verkaufstasten) ----------
    @GetMapping("/products/{id}/components")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public List<ComponentDto> getComponents(@PathVariable UUID id) {
        Product p = products.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        return p.getComponents().stream()
                .sorted(Comparator.comparing(pc -> pc.getComponentProduct().getName()))
                .map(ComponentDto::of).toList();
    }

    /** Replace-all semantics: the given list becomes the product's full recipe. Empty list = simple product again. */
    @PutMapping("/products/{id}/components")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ProductDto setComponents(@PathVariable UUID id, @RequestBody @Valid SetComponents body) {
        Product parent = products.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));

        Set<UUID> seen = new HashSet<>();
        List<ProductComponent> next = new ArrayList<>();
        for (NewComponent nc : body.components()) {
            if (!seen.add(nc.componentProductId())) {
                throw new ResponseStatusException(BAD_REQUEST, "Produkt mehrfach als Bestandteil angegeben");
            }
            if (nc.componentProductId().equals(parent.getId())) {
                throw new ResponseStatusException(BAD_REQUEST, "Ein Produkt kann sich nicht selbst enthalten");
            }
            Product comp = products.findById(nc.componentProductId())
                    .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Unbekannter Bestandteil: " + nc.componentProductId()));
            if (comp.isVariable()) {
                throw new ResponseStatusException(BAD_REQUEST, "„" + comp.getName() + "“ hat einen freien Preis und kann kein Bestandteil sein");
            }
            if (introducesCycle(parent, comp, new HashSet<>())) {
                throw new ResponseStatusException(BAD_REQUEST, "„" + comp.getName() + "“ würde einen Kompositions-Kreislauf erzeugen");
            }
            next.add(ProductComponent.builder()
                    .id(UUID.randomUUID())
                    .parentProduct(parent)
                    .componentProduct(comp)
                    .qty(nc.qty())
                    .build());
        }

        parent.getComponents().clear();
        parent.getComponents().addAll(next);
        parent.setComposed(!next.isEmpty());
        return ProductDto.of(saveProduct(parent));
    }

    /** True if making {@code candidate} a component of {@code parent} would create a cycle (candidate transitively contains parent). */
    private boolean introducesCycle(Product parent, Product candidate, Set<UUID> visited) {
        if (candidate.getId().equals(parent.getId())) return true;
        if (!visited.add(candidate.getId())) return false;
        for (ProductComponent pc : candidate.getComponents()) {
            if (introducesCycle(parent, pc.getComponentProduct(), visited)) return true;
        }
        return false;
    }

    @GetMapping("/products/export.csv")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<byte[]> exportCsv() {
        var w = new CsvWriter().row("Kategorie", "Produkt", "PLU", "Preis (€)", "Variabel", "Verkaufstaste", "Bestandteile");
        for (Category c : categories.findAllByOrderBySortOrderAsc()) {
            var sorted = c.getProducts().stream().sorted(Comparator.comparingInt(Product::getSortOrder)).toList();
            for (Product p : sorted) {
                String comps = p.getComponents().stream()
                        .sorted(Comparator.comparing(pc -> pc.getComponentProduct().getName()))
                        .map(pc -> pc.getQty() + "× " + pc.getComponentProduct().getName())
                        .reduce((a, b) -> a + ", " + b).orElse("");
                w.row(c.getName(), p.getName(), p.getPlu() == null ? "" : p.getPlu(), euro(p.getPriceCents()),
                        p.isVariable() ? "Ja" : "Nein", p.isComposed() ? "Ja" : "Nein", comps);
            }
        }
        return csv(w.toCsv(), "katalog-" + LocalDate.now(ZoneOffset.UTC) + ".csv");
    }

    private static ResponseEntity<byte[]> csv(String body, String filename) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=utf-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(bytes);
    }

    private static String normalisePlu(String plu) {
        if (plu == null) return null;
        String trimmed = plu.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Product saveProduct(Product p) {
        try {
            Product saved = products.saveAndFlush(p);
            return saved;
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(CONFLICT, "PLU bereits vergeben");
        }
    }
}
