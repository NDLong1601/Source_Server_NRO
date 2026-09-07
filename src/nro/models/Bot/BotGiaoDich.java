package nro.models.Bot;

import java.util.Random;
import java.util.ArrayList;
import java.util.List;
import nro.models.item.Item;
import nro.models.map.Zone;
import nro.models.map.service.ChangeMapService;
import nro.models.player.Player;
import nro.models.services.PlayerService;
import nro.models.services.ChatGlobalService;
import nro.models.services.ItemService;
import nro.models.services.Service;
import nro.models.services_func.Trade;

public class BotGiaoDich {
   public int idItem;
   public int idItTd;
   public int slot;
   
   private long lastimeChat;
   private long lastimeChatTrain;
   private Trade trade;
   
   public Bot bot;
   private Player pl;
   
   public BotGiaoDich(int item , int traodoi , int slot){
        this.idItem = item;
        this.idItTd = traodoi;
        this.slot = slot;
   }
   
   public BotGiaoDich(BotGiaoDich shop){
        this.idItem = shop.idItem;
        this.idItTd = shop.idItTd;
        this.slot = shop.slot;
   }
   
   public void update(){
       this.mapL();
       this.chat();
   }
   
   public String getChat(){
        Item it = ItemService.gI().createNewItem((short) this.idItem);
        Item it1 = ItemService.gI().createNewItem((short) this.idItTd);
        String text = String.format("p %s ja x%d %s/1 %s k%d", it.template.name , this.slot , it1.template.name , this.bot.zone.map.mapName , this.bot.zone.zoneId);
        return text;
   }
   
   public void chat(){
       if (this.lastimeChat < (System.currentTimeMillis() - ((100 + new Random().nextInt(100)) * 1000))){
       ChatGlobalService.gI().chat1(this.bot , this.getChat());
       this.lastimeChat = System.currentTimeMillis();
      }
     if (this.lastimeChatTrain < (System.currentTimeMillis() - ((5 + new Random().nextInt(5)) * 1000))){
      Service.gI().chat(this.bot , getChat());
      this.lastimeChatTrain = System.currentTimeMillis();
     }
   }
   
   
   public void activeTraDe(Player pl){
      trade = null;
      try {
         trade = new Trade(pl , bot);
         this.pl = pl;
         this.trade.openTabTrade();
      } catch (IllegalArgumentException | IllegalStateException ex) {
         Service.gI().sendThongBao(pl, "Không thể mở giao dịch với bot lúc này");
      }
   }
  
   public void CheckTraDe(List<Item> item){
    if (this.slot <= 0) {
        this.trade.cancelTrade(this.bot);
        return;
    }
    int slot1 = item.stream()
    .filter(it -> it != null && it.template != null && it.template.id == this.idItTd)
    .mapToInt(it -> it.quantity)
    .sum();
    boolean check = slot1 > 0;
     if (check){
         active(slot1);
     } else {
         this.trade.cancelTrade(this.bot);
     }
   }
   
   public void active(int sl){
       int sl1 = sl / this.slot;
       Item it = ItemService.gI().createNewItem((short) this.idItem , sl1);
       if (!this.trade.addItemBot(this.bot, it)) {
           this.trade.cancelTrade(this.bot);
           return;
       }
       this.trade.lockTran(this.bot);
       this.trade.acceptTrade(this.bot);
   }
   
   public void mapL(){
      if(this.bot.zone.map.mapId != 84){
          Zone zone = this.bot.getRandomZone(84);
          if(zone != null){
              ChangeMapService.gI().goToMap(this.bot, zone);
              this.bot.zone.load_Me_To_Another(this.bot);
              PlayerService.gI().playerMove(this.bot, 81 + new Random().nextInt(716), 336);
          }
      }
   }
}
