package nro.models.interfaces;

public interface INetwork extends Runnable {

    public INetwork init();

    public INetwork start(int var1) throws Exception;

    public INetwork setAcceptHandler(ISessionAcceptHandler var1);

    public INetwork close();

    public INetwork dispose();

    public INetwork setDoSomeThingWhenClose(IServerClose var1);

    public INetwork randomKey(boolean var1);

    public INetwork setTypeSessioClone(Class var1) throws Exception;

    public ISessionAcceptHandler getAcceptHandler() throws Exception;

    public boolean isRandomKey();

    public void stopConnect();
}
