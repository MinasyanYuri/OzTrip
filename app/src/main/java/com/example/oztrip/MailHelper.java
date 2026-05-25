package com.example.oztrip;

import android.content.Context;
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

    private static final String SENDER_EMAIL = "minasyanyuri910@gmail.com";
    private static final String SENDER_PASSWORD = "qogchhoxqmecmlcg";

    public static void sendSecurityAlert(Context context, String userEmail, String type) {
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

                    String subject = context.getString(R.string.text_auto_96) + (type.equals("login") ? context.getString(R.string.text_auto_97) : context.getString(R.string.text_auto_98));
                    String currentTime = new java.text.SimpleDateFormat("HH:mm:ss, dd.MM.yyyy",
                            java.util.Locale.getDefault()).format(new java.util.Date());

                    String body = context.getString(R.string.text_auto_99) + currentTime + ".\n" +
                            context.getString(R.string.text_auto_100);

                    message.setSubject(subject);
                    message.setText(body);
                    Transport.send(message);
                    android.util.Log.d("OZTRIP_MAIL", context.getString(R.string.text_auto_101) + userEmail);
                } catch (MessagingException e) {
                    android.util.Log.e("OZTRIP_MAIL", context.getString(R.string.text_auto_102) + e.getMessage());
                    e.printStackTrace();
                }
                return null;
            }
        }.execute();
    }
}