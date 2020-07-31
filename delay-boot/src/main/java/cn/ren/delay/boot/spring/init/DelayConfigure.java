package cn.ren.delay.boot.spring.init;

import org.springframework.context.annotation.Bean;

public class DelayConfigure {


    @Bean
    public SpringInitListenable createDeliveryInit(){
        return new SpringInitListenable();
    }
}
