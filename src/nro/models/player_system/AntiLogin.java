package nro.models.player_system;

/**
 *
 * @author By Mr Blue
 * 
 */

import nro.models.utils.Util;public class AntiLogin {

    private static final byte MAX_WRONG = 5;
    private static final int TIME_ANTI = 60000;   
    private long lastTimeLogin = -1;  
    private int timeCanLogin;
    public byte wrongLogin;    
    public boolean canLogin() {        
        if (lastTimeLogin != -1) {
            if (Util.canDoWithTime(lastTimeLogin, timeCanLogin)) {
                this.reset();  
                return true; 
            }
        }        
        return wrongLogin < MAX_WRONG;
    }
  
    public void wrong() {
        wrongLogin++;
      
        if (wrongLogin >= MAX_WRONG) {
            this.lastTimeLogin = System.currentTimeMillis();  
            this.timeCanLogin = TIME_ANTI;  
        }
    }

    /**
     * Reset lại số lần đăng nhập sai và các thông số liên quan
     */
    public void reset() {
        this.wrongLogin = 0; 
        this.lastTimeLogin = -1; 
        this.timeCanLogin = 0;  
    }

    
   public String getNotifyCannotLogin() {
    if (lastTimeLogin != -1) {
        long timeRemaining = (lastTimeLogin + timeCanLogin - System.currentTimeMillis()) / 1000;  // Số giây còn lại
        if (timeRemaining > 0) {
            return "Bạn đã đăng nhập tài khoản sai quá nhiều lần. Vui lòng thử lại sau " + timeRemaining + " giây.";
        }
    }
    return "Hãy thử đăng nhập lại"; 
}

}

