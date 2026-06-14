package com.inkflow.crm.module.email.template;

import com.inkflow.crm.module.email.enums.TemplateKey;

import java.util.Map;

public final class TemplateDefaults {

    private TemplateDefaults() {}

    private record Default(String subject, String body) {}

    private static final Map<TemplateKey, Default> DEFAULTS = build();

    public static RenderedContent get(TemplateKey key) {
        Default content = DEFAULTS.get(key);
        if (content == null) {
            throw new IllegalStateException("No default template for template key: " + key);
        }
        return new RenderedContent(content.subject(), content.body());
    }

    private static Map<TemplateKey, Default> build() {
        return Map.ofEntries(

            Map.entry(TemplateKey.WELCOME_ONBOARD, new Default(
                "Ласкаво просимо до {app_name}! 🚀",
                """
                Привіт, {user_name}!

                Робочий простір для студії {studio_name} активовано та готовий до роботи. Дякуємо, що обрали {app_name} для автоматизації вашого бізнесу.

                {app_name} звільняє ваш час від рутини: жодних паперових журналів і загублених записів — усе в одному зручному інтерфейсі. Щоб розпочати, додайте першого майстра або послугу.

                Потрібна допомога на старті? Перегляньте базу знань або просто дайте відповідь на цей лист — підтримка завжди поруч."""
            )),

            Map.entry(TemplateKey.TEAM_INVITE, new Default(
                "Запрошення до команди {studio_name}",
                """
                Привіт!

                {inviter_name} запрошує вас приєднатися до робочого простору {studio_name} у системі {app_name} у ролі {role}.

                Щоб прийняти запрошення, створити профіль та отримати доступ до розкладу, натисніть кнопку нижче.

                Посилання дійсне протягом 7 днів."""
            )),

            Map.entry(TemplateKey.ROLE_CHANGED, new Default(
                "Ваші права в {studio_name} оновлено",
                """
                Привіт, {user_name}!

                Адміністратор робочого простору {studio_name} змінив вашу роль на {role}. Це може вплинути на розділи системи, до яких ви маєте доступ.

                Якщо у вас є питання щодо нових прав — зверніться до керівництва студії."""
            )),

            Map.entry(TemplateKey.STAFF_DEACTIVATED, new Default(
                "Зміна статусу вашого акаунта в {studio_name}",
                """
                Привіт, {user_name}!

                Адміністратор робочого простору {studio_name} деактивував ваш обліковий запис у системі {app_name}. Ви більше не маєте доступу до розкладу, бази клієнтів та інструментів цієї студії.

                Якщо ви вважаєте, що сталася помилка, зверніться безпосередньо до керівництва {studio_name}."""
            )),

            Map.entry(TemplateKey.STAFF_REACTIVATED, new Default(
                "Ваш доступ до {studio_name} відновлено",
                """
                Привіт, {user_name}!

                Адміністратор робочого простору {studio_name} відновив ваш обліковий запис. Ви знову маєте доступ до розкладу та інструментів студії."""
            )),

            Map.entry(TemplateKey.TRIAL_STARTED, new Default(
                "Ваш пробний період {app_name} активовано",
                """
                Привіт, {user_name}!

                Ви отримали повний доступ до можливостей {app_name} безкоштовно на пробний період. Встигніть протестувати все без обмежень.

                Щоб не втратити доступ після завершення тріалу, додайте спосіб оплати у будь-який момент."""
            )),

            Map.entry(TemplateKey.TRIAL_EXPIRING, new Default(
                "Тріал {app_name} завершується через 3 дні",
                """
                Привіт, {user_name}!

                Пробний період для {studio_name} завершується незабаром. Щоб робота студії не зупинилася, оберіть тариф і додайте спосіб оплати.

                Усі ваші дані, записи й налаштування залишаться на місці — ви просто продовжите користуватися системою."""
            )),

            Map.entry(TemplateKey.BOOKING_REQUEST_RECEIVED, new Default(
                "Ми отримали вашу заявку до {studio_name}",
                """
                Привіт, {client_name}!

                Дякуємо за заявку до студії {studio_name}. Ми зв'яжемося з вами для підтвердження після того, як майстер перевірить доступність часу.

                Це ще не підтверджений запис — ми надішлемо окремий лист, щойно його буде підтверджено."""
            )),

            Map.entry(TemplateKey.BOOKING_CONFIRMED, new Default(
                "Ваш запис до {studio_name} підтверджено",
                """
                Привіт, {client_name}!

                Ваш запис у студію {studio_name} успішно створено. Ми чекаємо на вас!

                Дата: {date}
                Час: {time}
                Майстер: {master_name}
                Послуга: {service}
                Адреса: {address}

                Якщо плани зміняться — попередьте нас заздалегідь."""
            )),

            Map.entry(TemplateKey.BOOKING_REMINDER, new Default(
                "Нагадування: запис до {studio_name} {reminder_window}",
                """
                Привіт, {client_name}!

                Нагадуємо, що ми чекаємо на вас у студії {studio_name} {reminder_window}.

                Час: {time}
                Майстер: {master_name}
                Адреса: {address}

                Якщо не можете прийти — скасуйте запис, щоб майстер міг спланувати час."""
            )),

            Map.entry(TemplateKey.PREP_INSTRUCTIONS, new Default(
                "Як підготуватися до сеансу в {studio_name}",
                """
                Привіт, {client_name}!

                Щоб сеанс пройшов комфортно, а результат був ідеальним, дотримайтесь кількох порад напередодні:

                1. Добре виспіться та поїжте за 1–2 години до візиту.
                2. Не вживайте алкоголь і не зловживайте кавою за добу.
                3. Уникайте засмаги на ділянці майбутньої роботи.
                4. Одягніть зручний одяг, що відкриває потрібну зону.

                Питання щодо протипоказань? Напишіть нам у відповідь на цей лист."""
            )),

            Map.entry(TemplateKey.BOOKING_RESCHEDULED, new Default(
                "Час вашого запису змінено",
                """
                Привіт, {client_name}!

                Час або дату вашого запису до студії {studio_name} було змінено. Перевірте нові деталі:

                Нова дата: {date}
                Новий час: {time}
                Майстер: {master_name}
                Адреса: {address}

                Якщо новий час не підходить — зв'яжіться з нами."""
            )),

            Map.entry(TemplateKey.BOOKING_CANCELED, new Default(
                "Ваш запис скасовано",
                """
                Привіт, {client_name}!

                Ваш запис до студії {studio_name} на {date} о {time} до майстра {master_name} було скасовано.

                Якщо це сталося помилково або ви хочете обрати новий час — запишіться онлайн."""
            )),

            Map.entry(TemplateKey.AFTERCARE_INSTRUCTIONS, new Default(
                "Як доглядати за новим татуюванням 🩹",
                """
                Привіт, {client_name}!

                Дякуємо, що довірили свою роботу студії {studio_name} та майстру {master_name}. Вигляд татуювання тепер на 50% залежить від правильного догляду.

                Основні правила на найближчі дні:
                1. Знімайте плівку лише тоді, коли сказав майстер.
                2. Промивайте тату теплою водою з милом (без мочалки).
                3. Регулярно наносьте тонкий шар загоювального крему.
                4. Не чухайте, не розпарюйте у ванні та ховайте від прямого сонця.

                Якщо виникнуть питання — ми завжди на зв'язку."""
            )),

            Map.entry(TemplateKey.REVIEW_REQUEST, new Default(
                "Як все пройшло? Залиште відгук про {studio_name} ⭐",
                """
                Привіт, {client_name}!

                Нещодавно ви були на сеансі у майстра {master_name}. Сподіваємось, вам сподобався результат і атмосфера в студії!

                Будемо вдячні, якщо знайдете хвилинку залишити чесний відгук — це допомагає іншим клієнтам знайти свого майстра, а нам ставати кращими."""
            )),

            Map.entry(TemplateKey.NEW_APPOINTMENT, new Default(
                "Новий запис: {client_name} на {date}",
                """
                Привіт, {master_name}!

                У вашому розкладі з'явився новий запис.

                Клієнт: {client_name}
                Дата: {date}
                Час: {time}
                Послуга: {service}"""
            )),

            Map.entry(TemplateKey.APPOINTMENT_CANCELED, new Default(
                "❌ Запис скасовано: {client_name}",
                """
                Привіт, {master_name}!

                Запис клієнта {client_name} на {date} о {time} було скасовано. Цей час у розкладі знову доступний для бронювання."""
            )),

            Map.entry(TemplateKey.APPOINTMENT_CHANGED, new Default(
                "🔄 Запис перенесено: {client_name}",
                """
                Привіт, {master_name}!

                Час візиту клієнта {client_name} було змінено.

                Нова дата: {date}
                Новий час: {time}"""
            )),

            Map.entry(TemplateKey.NEW_REQUEST_TO_APPROVE, new Default(
                "🔔 Нова заявка від {client_name}",
                """
                Привіт, {user_name}!

                Надійшла нова заявка на запис, яка очікує на ваше рішення.

                Клієнт: {client_name}
                Послуга: {service}"""
            )),

            Map.entry(TemplateKey.BIRTHDAY, new Default(
                "З днем народження, {client_name}! 🎉",
                """
                Привіт, {client_name}!

                Команда студії {studio_name} вітає вас із днем народження! На честь свята даруємо вам приємний бонус на наступний візит."""
            )),

            Map.entry(TemplateKey.WINBACK, new Default(
                "Давно не бачились! Повертайтеся до {studio_name} 💜",
                """
                Привіт, {client_name}!

                Ви давно не заходили до студії {studio_name}. Можливо, саме час для нової роботи або корекції? Будемо раді бачити вас знову."""
            )),

            Map.entry(TemplateKey.BULK_EMAIL, new Default(
                "{studio_name}",
                ""
            ))
        );
    }

}
