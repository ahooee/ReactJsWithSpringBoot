package ir.linuxian.second.utils;

import java.security.SecureRandom;

public class RandomSix {



    public RandomSix(){
    }

    public Integer genRandom(){

        SecureRandom secureRandom  = new SecureRandom();

        return secureRandom.nextInt(100000,1000000);

    }

}
