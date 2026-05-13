package de.hems.kasse.catalog;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api")
public class CatalogController {

    private final CategoryRepository categories;
    private final ProductRepository products;

    public CatalogController(CategoryRepository categories, ProductRepository products) {
        this.categories = categories;
        this.products = products;
    }

    // ---------- DTOs ----------
    public record ProductDto(UUID id, String name, int priceCents, String color, int sortOrder) {
        static ProductDto of(Product p) {
            return new ProductDto(p.getId(), p.getName(), p.getPriceCents(), p.getColor(), p.getSortOrder());
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
                             @NotBlank @Size(max = 20) String color) {}
    public record PatchProduct(@Size(max = 120) String name,
                               Integer priceCents,
                               @Size(max = 20) String color,
                               Integer sortOrder,
                               UUID categoryId) {}

    // ---------- Reads ----------
    @GetMapping("/categories")
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
        if (!categories.existsById(id)) throw new ResponseStatusException(NOT_FOUND);
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
                .build();
        return ProductDto.of(products.save(p));
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
        return ProductDto.of(products.save(p));
    }

    @DeleteMapping("/products/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void deleteProduct(@PathVariable UUID id) {
        if (!products.existsById(id)) throw new ResponseStatusException(NOT_FOUND);
        products.deleteById(id);
    }
}
