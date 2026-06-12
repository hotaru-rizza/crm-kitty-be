package com.inkflow.crm.module.email.template;

import com.inkflow.crm.module.email.enums.TemplateKey;

import java.util.Map;

public final class TemplateDefaults {

    static final String DEFAULT_LOCALE = "uk";

    private TemplateDefaults() {}

    private record Default(String subject, String body) {}

    private static final Map<String, Map<TemplateKey, Default>> DEFAULTS = Map.of(
            "uk", buildUk(),
            "en", buildEn()
    );

    public static RenderedContent get(TemplateKey key, String locale) {
        Default content = lookup(key, locale);
        if (content == null) {
            content = lookup(key, DEFAULT_LOCALE);
        }
        return content != null
                ? new RenderedContent(content.subject(), content.body())
                : new RenderedContent("", "");
    }

    private static Default lookup(TemplateKey key, String locale) {
        Map<TemplateKey, Default> localeDefaults = DEFAULTS.get(locale);
        return localeDefaults != null ? localeDefaults.get(key) : null;
    }

    private static Map<TemplateKey, Default> buildUk() {
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

    private static Map<TemplateKey, Default> buildEn() {
        return Map.ofEntries(

            Map.entry(TemplateKey.WELCOME_ONBOARD, new Default(
                "Welcome to {app_name}! 🚀",
                """
                Hi {user_name}!

                Your workspace for {studio_name} is ready. Thank you for choosing {app_name} to automate your business.

                No more paper journals or lost bookings — everything in one convenient interface. To get started, add your first artist or service.

                Need help? Check out the knowledge base or reply to this email — support is always here."""
            )),

            Map.entry(TemplateKey.TEAM_INVITE, new Default(
                "You've been invited to {studio_name}",
                """
                Hi!

                {inviter_name} invites you to join the {studio_name} workspace in {app_name} as {role}.

                Click the button below to accept the invitation, create your profile and access the schedule.

                The link is valid for 7 days."""
            )),

            Map.entry(TemplateKey.ROLE_CHANGED, new Default(
                "Your access in {studio_name} has been updated",
                """
                Hi {user_name}!

                The administrator of {studio_name} changed your role to {role}. This may affect which sections of the system you can access.

                If you have questions about your new permissions, please contact studio management."""
            )),

            Map.entry(TemplateKey.STAFF_DEACTIVATED, new Default(
                "Your account status in {studio_name} has changed",
                """
                Hi {user_name}!

                The administrator of {studio_name} has deactivated your account in {app_name}. You no longer have access to the schedule, client base, or studio tools.

                If you believe this was a mistake, please contact {studio_name} management directly."""
            )),

            Map.entry(TemplateKey.STAFF_REACTIVATED, new Default(
                "Your access to {studio_name} has been restored",
                """
                Hi {user_name}!

                The administrator of {studio_name} has reactivated your account. You now have access to the schedule and studio tools again."""
            )),

            Map.entry(TemplateKey.TRIAL_STARTED, new Default(
                "Your {app_name} trial has started",
                """
                Hi {user_name}!

                You now have full access to {app_name} features for free during the trial period. Make the most of it!

                To avoid losing access after the trial ends, add a payment method at any time."""
            )),

            Map.entry(TemplateKey.TRIAL_EXPIRING, new Default(
                "Your {app_name} trial ends in 3 days",
                """
                Hi {user_name}!

                The trial period for {studio_name} is ending soon. To keep your studio running, choose a plan and add a payment method.

                All your data, appointments and settings will stay — you'll simply continue using the system."""
            )),

            Map.entry(TemplateKey.BOOKING_REQUEST_RECEIVED, new Default(
                "We received your request at {studio_name}",
                """
                Hi {client_name}!

                Thank you for submitting a request to {studio_name}. We will contact you to confirm once the artist checks availability.

                This is not yet a confirmed appointment — we'll send a separate email once it's confirmed."""
            )),

            Map.entry(TemplateKey.BOOKING_CONFIRMED, new Default(
                "Your appointment at {studio_name} is confirmed",
                """
                Hi {client_name}!

                Your appointment at {studio_name} has been successfully created. We look forward to seeing you!

                Date: {date}
                Time: {time}
                Artist: {master_name}
                Service: {service}
                Address: {address}

                If your plans change, please let us know in advance."""
            )),

            Map.entry(TemplateKey.BOOKING_REMINDER, new Default(
                "Reminder: appointment at {studio_name} {reminder_window}",
                """
                Hi {client_name}!

                Just a reminder that we're expecting you at {studio_name} {reminder_window}.

                Time: {time}
                Artist: {master_name}
                Address: {address}

                If you can't make it, please cancel so the artist can plan their schedule."""
            )),

            Map.entry(TemplateKey.PREP_INSTRUCTIONS, new Default(
                "How to prepare for your session at {studio_name}",
                """
                Hi {client_name}!

                To ensure your session goes smoothly and the result is perfect, here are a few tips:

                1. Get a good night's sleep and eat 1–2 hours before your visit.
                2. Avoid alcohol and limit caffeine the day before.
                3. Avoid tanning the area to be tattooed.
                4. Wear comfortable clothing that exposes the relevant area.

                Questions about contraindications? Reply to this email."""
            )),

            Map.entry(TemplateKey.BOOKING_RESCHEDULED, new Default(
                "Your appointment time has changed",
                """
                Hi {client_name}!

                The time or date of your appointment at {studio_name} has been changed. Please check the new details:

                New date: {date}
                New time: {time}
                Artist: {master_name}
                Address: {address}

                If the new time doesn't work for you, please contact us."""
            )),

            Map.entry(TemplateKey.BOOKING_CANCELED, new Default(
                "Your appointment has been canceled",
                """
                Hi {client_name}!

                Your appointment at {studio_name} on {date} at {time} with {master_name} has been canceled.

                If this was a mistake or you'd like to book a new time, please do so online."""
            )),

            Map.entry(TemplateKey.AFTERCARE_INSTRUCTIONS, new Default(
                "How to care for your new tattoo 🩹",
                """
                Hi {client_name}!

                Thank you for trusting {studio_name} and {master_name} with your tattoo. 50% of the final result depends on proper aftercare.

                Key rules for the next few days:
                1. Remove the film only when instructed by your artist.
                2. Wash the tattoo with warm water and mild soap (no scrubbing).
                3. Apply a thin layer of healing cream regularly.
                4. Avoid scratching, soaking in the bath, and direct sunlight.

                Any questions — we're always here."""
            )),

            Map.entry(TemplateKey.REVIEW_REQUEST, new Default(
                "How did it go? Leave a review for {studio_name} ⭐",
                """
                Hi {client_name}!

                You recently had a session with {master_name}. We hope you loved the result and the studio atmosphere!

                We'd be grateful if you could take a moment to leave an honest review — it helps other clients find their artist and helps us improve."""
            )),

            Map.entry(TemplateKey.NEW_APPOINTMENT, new Default(
                "New appointment: {client_name} on {date}",
                """
                Hi {master_name}!

                A new appointment has been added to your schedule.

                Client: {client_name}
                Date: {date}
                Time: {time}
                Service: {service}"""
            )),

            Map.entry(TemplateKey.APPOINTMENT_CANCELED, new Default(
                "❌ Appointment canceled: {client_name}",
                """
                Hi {master_name}!

                The appointment with {client_name} on {date} at {time} has been canceled. This time slot is now available again."""
            )),

            Map.entry(TemplateKey.APPOINTMENT_CHANGED, new Default(
                "🔄 Appointment rescheduled: {client_name}",
                """
                Hi {master_name}!

                The appointment time for {client_name} has been changed.

                New date: {date}
                New time: {time}"""
            )),

            Map.entry(TemplateKey.NEW_REQUEST_TO_APPROVE, new Default(
                "🔔 New request from {client_name}",
                """
                Hi {user_name}!

                A new booking request is awaiting your decision.

                Client: {client_name}
                Service: {service}"""
            )),

            Map.entry(TemplateKey.BIRTHDAY, new Default(
                "Happy birthday, {client_name}! 🎉",
                """
                Hi {client_name}!

                The team at {studio_name} wishes you a happy birthday! We have a special bonus for your next visit."""
            )),

            Map.entry(TemplateKey.WINBACK, new Default(
                "Long time no see! Come back to {studio_name} 💜",
                """
                Hi {client_name}!

                You haven't visited {studio_name} in a while. Maybe it's time for new work or a touch-up? We'd love to see you again."""
            )),

            Map.entry(TemplateKey.BULK_EMAIL, new Default(
                "{studio_name}",
                ""
            ))
        );
    }
}
