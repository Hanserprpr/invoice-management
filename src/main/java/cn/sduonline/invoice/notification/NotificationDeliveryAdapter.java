package cn.sduonline.invoice.notification;

import cn.sduonline.invoice.data.po.Notification;

public interface NotificationDeliveryAdapter {
    DeliveryResult deliver(Notification notification);

    record DeliveryResult(String outcome) { }
}
