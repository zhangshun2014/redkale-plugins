/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkalex.email;

import java.io.*;
import java.util.*;
import java.util.logging.*;
import javax.activation.*;
import javax.annotation.*;
import javax.mail.*;
import javax.mail.internet.*;
import javax.mail.util.*;
import org.redkale.service.*;
import org.redkale.util.*;
import static org.redkalex.email.EmailCodes.*;

/**
 *
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
@Local
@AutoLoad(false)
public class EmailService implements org.redkale.service.Service {

    private final Logger logger = Logger.getLogger(getClass().getSimpleName());

    private boolean fine = logger.isLoggable(Level.FINE);

    private final Properties mailprops = System.getProperties();

    @Resource(name = "property.mail.smtp.host")
    private String mail_host = "smtp.exmail.qq.com";  //demo使用企业QQ邮箱服务器 需要替换

    @Resource(name = "property.mail.smtp.port")
    private String mail_port = "465";

    @Resource(name = "property.mail.smtp.sslenable")
    private String mail_sslenable = "true";

    @Resource(name = "property.mail.from.account")
    private String mail_from_account = "demo@redkale.org";

    @Resource(name = "property.mail.from.password")
    private String mail_from_password = "demopassword";

    @Resource(name = "property.mail.smtp.host")
    private Authenticator mailauth;

    @ResourceListener
    void changeResource(String name, Object newVal, Object oldVal) {
        logger.info("@Resource = " + name + " resource changed:  newVal = " + newVal + ", oldVal = " + oldVal);
        init(null);
    }

    @Override
    public void init(AnyValue conf) {
        mailprops.setProperty("mail.smtp.host", mail_host);
        mailprops.setProperty("mail.smtp.port", mail_port);
        mailprops.setProperty("mail.smtp.auth", "true");
        mailprops.setProperty("mail.smtp.ssl.enable", mail_sslenable);
        mailauth = new Authenticator() {

            private PasswordAuthentication pa = new PasswordAuthentication(mail_from_account, mail_from_password);

            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return pa;
            }
        };
    }

    /**
     * 发送邮件
     *
     * @param bean
     *
     * @return
     */
    public RetResult sendMessage(EmailMessage bean) {
        long t1 = System.currentTimeMillis();
        if (bean == null) return retResult(RETMAIL_PARAM_ILLEAL);
        bean.setFrom(mail_from_account);
        if (mailauth == null) init(null);
        try {
            final Session session = Session.getInstance(mailprops, mailauth);
            Message msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(bean.getFromNames()));
            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(bean.getToNames(), false));
            if (bean.getCc() != null && !bean.getCc().isEmpty()) {
                msg.setRecipients(Message.RecipientType.CC, InternetAddress.parse(bean.getCcNames(), false));
            }
            if (bean.getBcc() != null && !bean.getBcc().isEmpty()) {
                msg.setRecipients(Message.RecipientType.BCC, InternetAddress.parse(bean.getBccNames(), false));
            }
            msg.setSubject(bean.getTitle());
            msg.setSentDate(new Date());
            String content = bean.getContent();
            if (!content.isEmpty() && bean.getContentType().contains("html") && bean.isHtmltranfer()) {
                StringBuilder sb = new StringBuilder(content.length());
                for (char ch : content.toCharArray()) {
                    if (ch < 255) {
                        sb.append(ch);
                    } else {
                        sb.append("&#").append((int) ch).append(";");
                    }
                }
                content = sb.toString();
            }
            if (bean.getFiles() == null) {
                msg.setDataHandler(new DataHandler(new ByteArrayDataSource(content, bean.getContentType())));
            } else {
                MimeMultipart mp = new MimeMultipart();
                MimeBodyPart ctx = new MimeBodyPart();
                ctx.setDataHandler(new DataHandler(new ByteArrayDataSource(content, bean.getContentType())));
                mp.addBodyPart(ctx);
                for (Map.Entry<String, Serializable> en : bean.getFiles().entrySet()) {
                    Serializable data = en.getValue();
                    MimeBodyPart mbp = new MimeBodyPart();
                    DataSource fds = (data instanceof byte[]) ? new ByteArrayDataSource((byte[]) data, bean.getContentType()) : new FileDataSource((String) data);
                    mbp.setDataHandler(new DataHandler(fds));
                    mbp.setFileName(MimeUtility.encodeWord(en.getKey(), "UTF-8", null));
                    mp.addBodyPart(mbp);
                }
                msg.setContent(mp);
            }
            Transport.send(msg);
            if (fine) logger.fine("sendmail over (" + bean + ") cost times:" + (System.currentTimeMillis() - t1) + "ms");
            return new RetResult();
        } catch (Exception ex) {
            logger.log(Level.WARNING, "sendmail error (" + bean + ") cost times:" + (System.currentTimeMillis() - t1) + "ms", ex);
            return retResult(RETMAIL_SEND_ERROR);
        }
    }

}
