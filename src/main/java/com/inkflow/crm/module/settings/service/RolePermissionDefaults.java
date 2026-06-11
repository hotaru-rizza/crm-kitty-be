package com.inkflow.crm.module.settings.service;

import com.inkflow.crm.domain.enums.Permission;

import java.util.Set;
import java.util.stream.Collectors;

public final class RolePermissionDefaults {

    private static final String SETTINGS_ROLES = Permission.SETTINGS_ROLES.getValue();

    private static final Set<String> ARTIST = Set.of(
            Permission.CLIENTS_VIEW_OWN.getValue(),
            Permission.PROJECTS_VIEW_OWN.getValue(),
            Permission.CALENDAR_VIEW_OWN.getValue(),
            Permission.CALENDAR_CREATE.getValue(),
            Permission.CALENDAR_EDIT.getValue(),
            Permission.CALENDAR_CANCEL.getValue(),
            Permission.LEAVES_VIEW.getValue(),
            Permission.LEAVES_CREATE.getValue(),
            Permission.SERVICES_VIEW.getValue(),
            Permission.LOCATIONS_VIEW.getValue(),
            Permission.EMAILS_VIEW.getValue(),
            Permission.PAYMENTS_VIEW.getValue(),
            Permission.FILES_UPLOAD.getValue()
    );

    private RolePermissionDefaults() {
    }

    public static boolean isGrantedForAdmin(Permission permission) {
        return !SETTINGS_ROLES.equals(permission.getValue());
    }

    public static boolean isGrantedForArtist(Permission permission) {
        return ARTIST.contains(permission.getValue());
    }

    public static Set<String> allPermissionValues() {
        return Set.of(Permission.values()).stream()
                .map(Permission::getValue)
                .collect(Collectors.toUnmodifiableSet());
    }
}
