package briServer;

public class BRiLaunch {

    public static final int PORT_AMA = 4000;
    public static final int PORT_PROG = 5000;

    public static void main(String[] args) {
        ServiceRegistry registry = new ServiceRegistry();

        // Tu peux démarrer avec un registre vide, puis ADD via clientProg
        System.out.println("BRiLaunch démarré.");
        System.out.println("PORT_AMA=" + PORT_AMA + " | PORT_PROG=" + PORT_PROG);

        new AmaServer(PORT_AMA, registry).start();
        new ProgServer(PORT_PROG, registry).start();
    }
}
