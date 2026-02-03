package com.inkflow.crm.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Permission {
    // Clients
    CLIENTS_VIEW_ALL("clients.view_all", "Клієнти", "Переглядати всіх клієнтів"),
    CLIENTS_VIEW_OWN("clients.view_own", "Клієнти", "Переглядати своїх клієнтів"),
    CLIENTS_CREATE("clients.create", "Клієнти", "Створювати клієнтів"),
    CLIENTS_EDIT("clients.edit", "Клієнти", "Редагувати клієнтів"),
    CLIENTS_DELETE("clients.delete", "Клієнти", "Видаляти клієнтів"),

    // Projects
    PROJECTS_VIEW_ALL("projects.view_all", "Проекти", "Переглядати всі проекти"),
    PROJECTS_VIEW_OWN("projects.view_own", "Проекти", "Переглядати свої проекти"),
    PROJECTS_CREATE("projects.create", "Проекти", "Створювати проекти"),
    PROJECTS_EDIT("projects.edit", "Проекти", "Редагувати проекти"),
    PROJECTS_DELETE("projects.delete", "Проекти", "Видаляти проекти"),

    // Requests
    REQUESTS_VIEW("requests.view", "Заявки", "Переглядати заявки"),
    REQUESTS_CREATE("requests.create", "Заявки", "Створювати заявки"),
    REQUESTS_CHANGE_STATUS("requests.change_status", "Заявки", "Змінювати статус заявок"),

    // Calendar
    CALENDAR_VIEW_ALL("calendar.view_all", "Календар", "Переглядати всі записи"),
    CALENDAR_VIEW_OWN("calendar.view_own", "Календар", "Переглядати свої записи"),
    CALENDAR_CREATE("calendar.create", "Календар", "Створювати записи"),
    CALENDAR_EDIT("calendar.edit", "Календар", "Редагувати записи"),
    CALENDAR_CANCEL("calendar.cancel", "Календар", "Скасовувати записи"),

    // Staff
    STAFF_VIEW("staff.view", "Команда", "Переглядати команду"),
    STAFF_INVITE("staff.invite", "Команда", "Запрошувати учасників"),
    STAFF_EDIT("staff.edit", "Команда", "Редагувати учасників"),

    // Finance
    FINANCE_VIEW("finance.view", "Фінанси", "Переглядати фінанси"),
    FINANCE_CREATE("finance.create", "Фінанси", "Створювати транзакції"),

    // Settings
    SETTINGS_ACCESS("settings.access", "Налаштування", "Доступ до налаштувань"),
    SETTINGS_ROLES("settings.roles", "Налаштування", "Керувати ролями");

    private final String value;
    private final String category;
    private final String description;

    public static Permission fromValue(String value) {
        for (Permission p : values()) {
            if (p.value.equals(value)) return p;
        }
        throw new IllegalArgumentException("Unknown permission: " + value);
    }
}
