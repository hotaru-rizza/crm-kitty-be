package com.inkflow.crm.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Permission {

    CLIENTS_VIEW_ALL("clients.view_all", "Клієнти", "Переглядати всіх клієнтів"),
    CLIENTS_VIEW_OWN("clients.view_own", "Клієнти", "Переглядати своїх клієнтів"),
    CLIENTS_CREATE("clients.create", "Клієнти", "Створювати клієнтів"),
    CLIENTS_EDIT("clients.edit", "Клієнти", "Редагувати клієнтів"),
    CLIENTS_DELETE("clients.delete", "Клієнти", "Видаляти клієнтів"),


    PROJECTS_VIEW_ALL("projects.view_all", "Проекти", "Переглядати всі проекти"),
    PROJECTS_VIEW_OWN("projects.view_own", "Проекти", "Переглядати свої проекти"),
    PROJECTS_CREATE("projects.create", "Проекти", "Створювати проекти"),
    PROJECTS_EDIT("projects.edit", "Проекти", "Редагувати проекти"),
    PROJECTS_DELETE("projects.delete", "Проекти", "Видаляти проекти"),


    REQUESTS_VIEW("requests.view", "Заявки", "Переглядати заявки"),
    REQUESTS_CREATE("requests.create", "Заявки", "Створювати заявки"),
    REQUESTS_CHANGE_STATUS("requests.change_status", "Заявки", "Змінювати статус заявок"),


    CALENDAR_VIEW_ALL("calendar.view_all", "Календар", "Переглядати всі записи"),
    CALENDAR_VIEW_OWN("calendar.view_own", "Календар", "Переглядати свої записи"),
    CALENDAR_CREATE("calendar.create", "Календар", "Створювати записи"),
    CALENDAR_EDIT("calendar.edit", "Календар", "Редагувати записи"),
    CALENDAR_CANCEL("calendar.cancel", "Календар", "Скасовувати записи"),


    STAFF_VIEW("staff.view", "Команда", "Переглядати команду"),
    STAFF_INVITE("staff.invite", "Команда", "Запрошувати учасників"),
    STAFF_EDIT("staff.edit", "Команда", "Редагувати учасників"),


    FINANCE_VIEW("finance.view", "Фінанси", "Переглядати фінанси"),
    FINANCE_CREATE("finance.create", "Фінанси", "Створювати транзакції"),


    SETTINGS_ACCESS("settings.access", "Налаштування", "Доступ до налаштувань"),
    SETTINGS_ROLES("settings.roles", "Налаштування", "Керувати ролями"),
    AUDIT_VIEW("audit.view", "Налаштування", "Переглядати журнал активності"),


    LEAVES_VIEW("leaves.view", "Відпустки", "Переглядати відпустки"),
    LEAVES_CREATE("leaves.create", "Відпустки", "Створювати заявки на відпустку"),
    LEAVES_MANAGE("leaves.manage", "Відпустки", "Керувати заявками на відпустку"),


    SERVICES_VIEW("services.view", "Послуги", "Переглядати послуги"),
    SERVICES_EDIT("services.edit", "Послуги", "Редагувати послуги"),


    LOCATIONS_VIEW("locations.view", "Локації", "Переглядати локації"),
    LOCATIONS_EDIT("locations.edit", "Локації", "Редагувати локації"),


    EMAILS_VIEW("emails.view", "Email", "Переглядати email-лог"),
    EMAILS_SEND("emails.send", "Email", "Надсилати email"),
    EMAILS_MANAGE("emails.manage", "Email", "Керувати шаблонами та налаштуваннями email"),


    PAYMENTS_VIEW("payments.view", "Платежі", "Переглядати платежі"),
    PAYMENTS_PROCESS("payments.process", "Платежі", "Обробляти платежі та рахунки"),


    FILES_UPLOAD("files.upload", "Файли", "Завантажувати файли");

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
