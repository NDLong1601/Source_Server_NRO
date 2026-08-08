package nro.models.interfaces;


public interface ISessionAcceptHandler {
    public void sessionInit(ISession var1);

    public void sessionDisconnect(ISession var1);
}

