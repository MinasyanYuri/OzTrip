package com.example.oztrip;

import android.os.AsyncTask;
import java.util.Properties;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class MailHelper {

    // ДАННЫЕ ТВОЕЙ ТЕХНИЧЕСКОЙ ПОЧТЫ
    private static final String SENDER_EMAIL = "minasyanyuri910@gmail.com";
    private static final String SENDER_PASSWORD = "qogchhoxqmecmlcg"; // Вставляем БЕЗ пробелов

    public static void sendSecurityAlert(String userEmail, String type) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                Properties props = new Properties();
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.host", "smtp.gmail.com");
                props.put("mail.smtp.port", "587");

                Session session = Session.getInstance(props, new javax.mail.Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(SENDER_EMAIL, SENDER_PASSWORD);
                    }
                });

                try {
                    Message message = new MimeMessage(session);
                    message.setFrom(new InternetAddress(SENDER_EMAIL));
                    message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(userEmail));

                    String subject = "Безопасность OzTrip: " + (type.equals("login") ? "Новый вход" : "Регистрация");
                    String currentTime = new java.text.SimpleDateFormat("HH:mm:ss, dd.MM.yyyy",
                            java.util.Locale.getDefault()).format(new java.util.Date());

                    String body = "Внимание! В ваш аккаунт OzTrip вошли в " + currentTime + ".\n" +
                            "Если это были не вы — заблокируйте доступ.";

                    message.setSubject(subject);
                    message.setText(body);
                    Transport.send(message);
                    android.util.Log.d("OZTRIP_MAIL", "Письмо успешно отправлено на: " + userEmail);
                } catch (MessagingException e) {
                    e.printStackTrace();
                    // ЕСЛИ ОШИБКА — УВИДИМ ПРИЧИНУ
                    android.util.Log.e("OZTRIP_MAIL", "ОШИБКА ОТПРАВКИ: " + e.getMessage());
                    e.printStackTrace();
                }
                return null;
            }
        }.execute();
    }
}