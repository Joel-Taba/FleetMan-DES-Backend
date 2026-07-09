package com.yowyob.fleet.infrastructure.adapters.outbound.messaging;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.test.StepVerifier;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class SmtpMailAdapterTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

    @Test
    void sendEmail_deliversMessageToSmtpServer() throws Exception {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost("localhost");
        mailSender.setPort(ServerSetupTest.SMTP.getPort());
        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.smtp.auth", "false");
        props.put("mail.smtp.starttls.enable", "false");

        SmtpMailAdapter adapter = new SmtpMailAdapter(mailSender);
        ReflectionTestUtils.setField(adapter, "enabled", true);
        ReflectionTestUtils.setField(adapter, "from", "fleetman@test.local");
        ReflectionTestUtils.setField(adapter, "fromName", "FleetMan Test");
        ReflectionTestUtils.setField(adapter, "smtpUsername", "test@local");

        StepVerifier.create(adapter.sendEmail(
                        "joeltaba4@gmail.com",
                        "Rejet de votre demande FleetMan",
                        "Motif de test automatisé"))
                .expectNext(true)
                .verifyComplete();

        assertThat(greenMail.getReceivedMessages()).hasSize(1);
        MimeMessage received = greenMail.getReceivedMessages()[0];
        assertThat(received.getAllRecipients()[0].toString()).contains("joeltaba4@gmail.com");
        assertThat(GreenMailUtil.getBody(received)).containsIgnoringCase("automatis");
    }

    @Test
    void sendEmail_returnsFalseWhenSmtpUsernameMissing() {
        SmtpMailAdapter adapter = new SmtpMailAdapter(new JavaMailSenderImpl());
        ReflectionTestUtils.setField(adapter, "enabled", true);
        ReflectionTestUtils.setField(adapter, "smtpUsername", "");

        StepVerifier.create(adapter.sendEmail("a@b.com", "s", "b"))
                .expectNext(false)
                .verifyComplete();
    }
}
