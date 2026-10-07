package com.redur.electra.ui.menu;

import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.navigation.NavOptions;

import com.redur.electra.R;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Relaciona cada id de menú del servidor con su literal ({@code R.string.menu_<id>}), su icono y
 * lo que hace al pulsarlo. Un menú que llega del servidor sin registrar se muestra con el texto
 * del servidor, un icono genérico y avisa de que aún no está disponible.
 */
public final class MenuActionRegistry {

    /** Inicio no llega del servidor: es fijo y siempre encabeza el menú. */
    public static final int HOME_MENU_ID = 0;

    private record MenuEntry(@StringRes int title, @DrawableRes int icon, @Nullable MenuAction action) {
    }

    private static final Map<Integer, MenuEntry> MENU_ENTRY_MAP = new HashMap<>();
    /** Destino de pantalla completa → menú que lo abre, para marcar la opción activa. */
    private static final Map<Integer, Integer> MENU_BY_DESTINATION = new HashMap<>();

    /**
     * Las pantallas del menú son hermanas: se vuelve siempre a inicio en lugar de apilarlas, y
     * pulsar la opción ya abierta no crea una copia.
     */
    private static final NavOptions TOP_LEVEL_OPTIONS = new NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setPopUpTo(R.id.nav_home, false)
            .build();

    static {
        // Registrar todas las acciones del Drawer
        screen(HOME_MENU_ID, R.string.menu_0, R.drawable.ic_home_24, R.id.nav_home);
        // Pantallas aún sin implementar: se registran sus literales y se avisa al pulsarlas
        pending(1, R.string.menu_1, R.drawable.ic_local_shipping_24);
        pending(2, R.string.menu_2, R.drawable.ic_receipt_24);
        // Agrupador: al pulsarlo despliega sus submenús
        pending(3, R.string.menu_3, R.drawable.ic_account_tree_24);
        dialog(4, R.string.menu_4, R.drawable.ic_swap_horiz_24, R.id.nav_cambiar_plaza);
        dialog(5, R.string.menu_5, R.drawable.ic_photo_camera_24, R.id.nav_toma_foto);
        dialog(6, R.string.menu_6, R.drawable.ic_label_24, R.id.nav_label_options);
        screen(7, R.string.menu_7, R.drawable.ic_local_shipping_24, R.id.nav_bulto_type);
        // Agrupador de prototipos: al pulsarlo despliega sus submenús
        pending(8, R.string.menu_8, R.drawable.ic_folder_24);
        screen(9, R.string.menu_9, R.drawable.ic_scale_24, R.id.nav_bulto_weight);
    }

    private MenuActionRegistry() {
    }

    /** Acción del menú, o null si no está registrado o su pantalla aún no existe. */
    @Nullable
    public static MenuAction getAction(int menuId) {
        MenuEntry entry = MENU_ENTRY_MAP.get(menuId);
        return entry != null ? entry.action() : null;
    }

    /** Literal del menú, o null si no está registrado (se usa entonces el texto del servidor). */
    @Nullable
    @StringRes
    public static Integer getTitle(int menuId) {
        MenuEntry entry = MENU_ENTRY_MAP.get(menuId);
        return entry != null ? entry.title() : null;
    }

    /** Icono del menú; si no está registrado, uno genérico de carpeta (con submenús) o de pantalla. */
    @DrawableRes
    public static int getIcon(int menuId, boolean hasChildren) {
        MenuEntry entry = MENU_ENTRY_MAP.get(menuId);
        if (entry != null) {
            return entry.icon();
        }
        return hasChildren ? R.drawable.ic_folder_24 : R.drawable.ic_description_24;
    }

    /** Menú que abre el destino, o null si el destino no es una pantalla del menú. */
    @Nullable
    public static Integer findMenuId(@IdRes int destinationId) {
        return MENU_BY_DESTINATION.get(destinationId);
    }

    /** Destinos del menú: en ellos la toolbar muestra el botón del menú en lugar de "atrás". */
    @NonNull
    public static Set<Integer> getTopLevelDestinations() {
        return new HashSet<>(MENU_BY_DESTINATION.keySet());
    }

    private static void screen(int menuId, @StringRes int title, @DrawableRes int icon, @IdRes int destinationId) {
        MENU_BY_DESTINATION.put(destinationId, menuId);
        MENU_ENTRY_MAP.put(menuId, new MenuEntry(title, icon,
                (navController, args) -> navController.navigate(destinationId, args, TOP_LEVEL_OPTIONS)));
    }

    /** Hojas y diálogos: se abren sobre la pantalla actual sin sustituirla. */
    private static void dialog(int menuId, @StringRes int title, @DrawableRes int icon, @IdRes int destinationId) {
        MENU_ENTRY_MAP.put(menuId, new MenuEntry(title, icon,
                (navController, args) -> navController.navigate(destinationId, args)));
    }

    private static void pending(int menuId, @StringRes int title, @DrawableRes int icon) {
        MENU_ENTRY_MAP.put(menuId, new MenuEntry(title, icon, null));
    }
}
