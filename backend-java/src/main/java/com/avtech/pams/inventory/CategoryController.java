package com.avtech.pams.inventory;

import com.avtech.pams.audit.AuditService;
import com.avtech.pams.common.ApiException;
import com.avtech.pams.inventory.InventoryViews.CategoryView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Item categories, one level of sub-categories deep. Listing is open to any signed-in user (pickers
 * need it); maintaining them needs inventory rights. The Laravel app could only list them.
 */
@RestController
@RequestMapping("/api/inventory/categories")
public class CategoryController {

    private final CategoryRepository categories;
    private final ItemRepository items;
    private final AuditService audit;

    public CategoryController(CategoryRepository categories, ItemRepository items, AuditService audit) {
        this.categories = categories;
        this.items = items;
        this.audit = audit;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<CategoryView> index() {
        Map<Long, Long> counts = new HashMap<>();
        categories.itemCounts().forEach(row -> counts.put((Long) row[0], (Long) row[1]));
        List<Category> all = categories.findAll(Sort.by("name"));
        // Parents first, each followed by its children.
        List<CategoryView> out = new java.util.ArrayList<>();
        for (Category parent : all) {
            if (parent.getParentId() != null) continue;
            out.add(view(parent, counts));
            for (Category child : all) {
                if (Objects.equals(child.getParentId(), parent.getId())) out.add(view(child, counts));
            }
        }
        return out;
    }

    public record CategoryRequest(
            @NotBlank(message = "Enter the category name.") @Size(max = 255, message = "The name may not exceed 255 characters.") String name,
            Long parentId) {
    }

    @PostMapping
    @PreAuthorize("@perm.any('create_inventory', 'edit_inventory')")
    @Transactional
    public ResponseEntity<CategoryView> store(@Valid @RequestBody CategoryRequest body) {
        String name = body.name().trim();
        Category parent = parent(body.parentId(), null);
        ensureUniqueName(name, body.parentId(), null);
        if (parent != null && items.existsByCategoryId(parent.getId())) {
            throw ApiException.validation("parent_id",
                    "\"" + parent.getName() + "\" already has items. Move them to a sub-category before adding sub-categories.");
        }
        Category c = new Category();
        c.setName(name);
        c.setParentId(body.parentId());
        categories.save(c);
        if (parent != null) parent.setLeaf(false);
        audit.record("created", AuditService.CATEGORY, c.getId(), "Created Category " + name, null,
                Map.of("name", name, "parent_id", String.valueOf(body.parentId())));
        return ResponseEntity.status(HttpStatus.CREATED).body(view(c, Map.of()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.any('edit_inventory')")
    @Transactional
    public CategoryView update(@PathVariable Long id, @Valid @RequestBody CategoryRequest body) {
        Category c = categories.findById(id).orElseThrow(() -> ApiException.notFound("Category"));
        String name = body.name().trim();
        if (!Objects.equals(body.parentId(), c.getParentId())) {
            if (categories.existsByParentId(id)) {
                throw ApiException.validation("parent_id", "A category with sub-categories cannot itself become a sub-category.");
            }
            parent(body.parentId(), id);
        }
        ensureUniqueName(name, body.parentId(), id);
        Map<String, Object> before = Map.of("name", c.getName(), "parent_id", String.valueOf(c.getParentId()));
        Long oldParent = c.getParentId();
        c.setName(name);
        c.setParentId(body.parentId());
        categories.flush();
        refreshLeaf(oldParent);
        refreshLeaf(body.parentId());
        audit.record("updated", AuditService.CATEGORY, id, "Updated Category " + name, before,
                Map.of("name", name, "parent_id", String.valueOf(body.parentId())));
        return view(c, Map.of());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.any('delete_inventory')")
    @Transactional
    public Map<String, Object> destroy(@PathVariable Long id) {
        Category c = categories.findById(id).orElseThrow(() -> ApiException.notFound("Category"));
        if (categories.existsByParentId(id)) {
            throw ApiException.of(HttpStatus.CONFLICT, "Delete or move this category's sub-categories first.");
        }
        if (items.existsByCategoryId(id)) {
            throw ApiException.of(HttpStatus.CONFLICT, "Move this category's items to another category first.");
        }
        Long parentId = c.getParentId();
        categories.delete(c);
        categories.flush();
        refreshLeaf(parentId);
        audit.record("deleted", AuditService.CATEGORY, id, "Deleted Category " + c.getName(),
                Map.of("name", c.getName(), "parent_id", String.valueOf(parentId)), null);
        return Map.of("message", "Category deleted.");
    }

    /** Only top-level categories can have children, and a category can't be its own parent. */
    private Category parent(Long parentId, Long selfId) {
        if (parentId == null) return null;
        if (parentId.equals(selfId)) throw ApiException.validation("parent_id", "A category cannot be its own parent.");
        Category p = categories.findById(parentId).orElseThrow(() -> ApiException.validation("parent_id", "Choose a valid parent category."));
        if (p.getParentId() != null) {
            throw ApiException.validation("parent_id", "Sub-categories can only go one level deep; choose a top-level category.");
        }
        return p;
    }

    private void ensureUniqueName(String name, Long parentId, Long selfId) {
        boolean taken = parentId == null
                ? categories.existsByNameIgnoreCaseAndParentIdIsNull(name)
                : categories.existsByNameIgnoreCaseAndParentId(name, parentId);
        if (taken && (selfId == null || !categories.findById(selfId).map(c -> c.getName().equalsIgnoreCase(name)
                && Objects.equals(c.getParentId(), parentId)).orElse(false))) {
            throw ApiException.validation("name", "A category with this name already exists here.");
        }
    }

    private void refreshLeaf(Long categoryId) {
        if (categoryId == null) return;
        categories.findById(categoryId).ifPresent(p -> p.setLeaf(!categories.existsByParentId(categoryId)));
    }

    private static CategoryView view(Category c, Map<Long, Long> counts) {
        return new CategoryView(c.getId(), c.getName(), c.getParentId(), c.isLeaf(), counts.getOrDefault(c.getId(), 0L));
    }
}
