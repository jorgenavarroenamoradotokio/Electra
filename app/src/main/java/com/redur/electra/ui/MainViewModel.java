package com.redur.electra.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.redur.electra.core.ui.UiText;
import com.redur.electra.data.model.user.MenuItem;
import com.redur.electra.data.model.user.User;
import com.redur.electra.data.session.UserSession;
import com.redur.electra.ui.menu.DrawerMenuEntry;
import com.redur.electra.ui.menu.DrawerMenuRow;
import com.redur.electra.ui.menu.MenuActionRegistry;
import com.redur.electra.ui.menu.MenuArgs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class MainViewModel extends ViewModel {

    private final UserSession session;

    private final MutableLiveData<List<DrawerMenuRow>> menuRows = new MutableLiveData<>();
    /** Árbol del menú de la sesión; se construye al primer uso (la sesión ya está iniciada). */
    @Nullable
    private List<DrawerMenuEntry> menuTree;
    private final Set<Integer> expandedGroups = new HashSet<>();
    private int selectedMenuId = MenuActionRegistry.HOME_MENU_ID;

    @Inject
    public MainViewModel(UserSession session) {
        this.session = session;
    }

    /** Nombre con el que se presenta al usuario: el completo si existe, si no su usuario. */
    @Nullable
    public String getUserDisplayName() {
        User user = session.getUser();
        return user != null ? user.displayName() : null;
    }

    @Nullable
    public String getUsername() {
        User user = session.getUser();
        return user != null ? user.username() : null;
    }

    /** Inicial del nombre para el avatar, o null sin sesión. */
    @Nullable
    public String getUserInitial() {
        String name = getUserDisplayName();
        if (name == null || name.isBlank()) {
            return null;
        }
        return new String(Character.toChars(name.strip().codePointAt(0))).toUpperCase(Locale.ROOT);
    }

    /**
     * Filas visibles del menú: Inicio fijo y después el menú de la sesión en el orden del servidor,
     * agrupado por {@code parentId}. Los submenús empiezan plegados.
     */
    @NonNull
    public LiveData<List<DrawerMenuRow>> getMenuRows() {
        if (menuRows.getValue() == null) {
            publishRows();
        }
        return menuRows;
    }

    /** Despliega o pliega un menú con submenús. */
    public void onGroupToggled(int menuId) {
        if (!expandedGroups.remove(menuId)) {
            expandedGroups.add(menuId);
        }
        publishRows();
    }

    /**
     * Marca la opción de la pantalla que se está mostrando y despliega sus menús padre, para que
     * la opción activa quede siempre a la vista.
     */
    public void onMenuShown(int menuId) {
        selectedMenuId = menuId;
        expandPathTo(menuTree(), menuId);
        publishRows();
    }

    /**
     * Permisos del menú pulsado leídos de la sesión, para pasárselos a la pantalla que abre.
     * Inicio no tiene permisos. Null si el menú no pertenece al usuario en sesión.
     */
    @Nullable
    public MenuArgs getMenuArgs(int menuId) {
        User user = session.getUser();
        if (user == null) {
            return null;
        }
        if (menuId == MenuActionRegistry.HOME_MENU_ID) {
            return new MenuArgs(menuId, Set.of());
        }
        for (MenuItem item : user.menu()) {
            if (item.id() == menuId) {
                return new MenuArgs(menuId, item.permissions());
            }
        }
        return null;
    }

    private void publishRows() {
        List<DrawerMenuRow> rows = new ArrayList<>();
        addRows(menuTree(), 0, rows);
        menuRows.setValue(rows);
    }

    private void addRows(@NonNull List<DrawerMenuEntry> entries, int depth, @NonNull List<DrawerMenuRow> rows) {
        for (DrawerMenuEntry entry : entries) {
            boolean group = entry.hasChildren();
            boolean expanded = group && expandedGroups.contains(entry.menuId());
            rows.add(new DrawerMenuRow(entry.menuId(), entry.title(),
                    MenuActionRegistry.getIcon(entry.menuId(), group), depth, group, expanded,
                    entry.menuId() == selectedMenuId));
            if (expanded) {
                addRows(entry.children(), depth + 1, rows);
            }
        }
    }

    private boolean expandPathTo(@NonNull List<DrawerMenuEntry> entries, int menuId) {
        for (DrawerMenuEntry entry : entries) {
            if (entry.menuId() == menuId) {
                return true;
            }
            if (expandPathTo(entry.children(), menuId)) {
                expandedGroups.add(entry.menuId());
                return true;
            }
        }
        return false;
    }

    @NonNull
    private List<DrawerMenuEntry> menuTree() {
        if (menuTree == null) {
            menuTree = buildMenuTree();
        }
        return menuTree;
    }

    /** Un hijo cuyo padre no llega en la lista se muestra en el primer nivel para no perder la opción. */
    @NonNull
    private List<DrawerMenuEntry> buildMenuTree() {
        List<DrawerMenuEntry> entries = new ArrayList<>();
        entries.add(new DrawerMenuEntry(MenuActionRegistry.HOME_MENU_ID,
                titleOf(MenuActionRegistry.HOME_MENU_ID, ""), List.of()));
        User user = session.getUser();
        if (user == null) {
            return entries;
        }
        Set<Integer> ids = new HashSet<>();
        Map<Integer, List<MenuItem>> childrenByParent = new HashMap<>();
        for (MenuItem item : user.menu()) {
            ids.add(item.id());
            if (!item.isRoot()) {
                childrenByParent.computeIfAbsent(item.parentId(), key -> new ArrayList<>()).add(item);
            }
        }
        // Inicio ya está: si el servidor también lo envía no se duplica
        Set<Integer> visited = new HashSet<>(Set.of(MenuActionRegistry.HOME_MENU_ID));
        for (MenuItem item : user.menu()) {
            if ((item.isRoot() || !ids.contains(item.parentId())) && visited.add(item.id())) {
                entries.add(toEntry(item, childrenByParent, visited));
            }
        }
        return entries;
    }

    /** {@code visited} evita bucles si el servidor repite ids o encadena padres entre sí. */
    @NonNull
    private DrawerMenuEntry toEntry(@NonNull MenuItem item,
                                    @NonNull Map<Integer, List<MenuItem>> childrenByParent,
                                    @NonNull Set<Integer> visited) {
        List<DrawerMenuEntry> children = new ArrayList<>();
        for (MenuItem child : childrenByParent.getOrDefault(item.id(), List.of())) {
            if (visited.add(child.id())) {
                children.add(toEntry(child, childrenByParent, visited));
            }
        }
        return new DrawerMenuEntry(item.id(), titleOf(item.id(), item.text()), children);
    }

    @NonNull
    private UiText titleOf(int menuId, @NonNull String serverText) {
        Integer title = MenuActionRegistry.getTitle(menuId);
        return title != null ? new UiText.Res(title) : new UiText.Raw(serverText);
    }
}
