package ir.linuxian.telegram;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class TelegramService implements AutoCloseable {
    static {
        try {
            System.loadLibrary("tdjni");
        } catch (UnsatisfiedLinkError first) {
            // Running as a module of another project (e.g. second) often means
            // java.library.path was not set for TDchatBot/native. Try known
            // relative locations before giving up, so `bootRun` / `java -jar`
            // work even without an explicit -Djava.library.path.
            // The native lib is embedded at <project>/native. Older checkouts may
            // still run with the TDchatBot folder next to second, so those
            // locations are kept as fallbacks (harmless once it is removed).
            String[] candidates = {
                    "native/libtdjni.so",
                    "../TDchatBot/native/libtdjni.so",
                    "TDchatBot/native/libtdjni.so",
                    "../native/libtdjni.so",
            };
            boolean loaded = false;
            UnsatisfiedLinkError last = first;
            for (String candidate : candidates) {
                try {
                    File f = new File(candidate).getAbsoluteFile();
                    if (f.isFile()) {
                        System.load(f.getAbsolutePath());
                        loaded = true;
                        break;
                    }
                } catch (UnsatisfiedLinkError e) {
                    last = e;
                }
            }
            if (!loaded) {
                throw last;
            }
        }
    }

    private final List<Consumer<TdApi.Object>> updateListeners = new CopyOnWriteArrayList<>();
    private final int apiId;
    private final String apiHash;
    private final LoginPrompt prompt;

    private final CountDownLatch clientAssigned = new CountDownLatch(1);
    private final CountDownLatch loggedIn = new CountDownLatch(1);
    private volatile Client client;

    private final List<Consumer<TdApi.Message>> messageListeners = new CopyOnWriteArrayList<>();

    // TDLib session directory. Defaults to the standalone layout ("tdlib-data"
    // next to the working dir). When embedded in another app (e.g. second),
    // set this to the shared session dir so login is not required again.
    private volatile String databaseDirectory = "tdlib-data";

    // Listeners run on their own thread so they may safely make blocking calls.
    private final ExecutorService listenerPool = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "td-listeners");
        t.setDaemon(true);
        return t;
    });

    /** Optional callback for auth-step errors (bad code, wrong password…),
     * so an embedding app can surface them in its own login UI. */
    private volatile Consumer<String> authErrorListener;

    public TelegramService(int apiId, String apiHash, LoginPrompt prompt) {
        this.apiId = apiId;
        this.apiHash = apiHash;
        this.prompt = prompt;
    }

    public TelegramService(int apiId, String apiHash, LoginPrompt prompt, String databaseDirectory) {
        this(apiId, apiHash, prompt);
        setDatabaseDirectory(databaseDirectory);
    }

    /** Optional callback for auth-step errors (bad code, wrong password…). */
    public void setAuthErrorListener(Consumer<String> authErrorListener) {
        this.authErrorListener = authErrorListener;
    }

    public String getDatabaseDirectory() {
        return databaseDirectory;
    }

    public void setDatabaseDirectory(String databaseDirectory) {
        if (databaseDirectory != null && !databaseDirectory.isBlank()) {
            this.databaseDirectory = databaseDirectory;
        }
    }

    // ---------- public API ----------

    /** Starts TDLib and blocks until login is complete. */
    public void start() throws InterruptedException, Client.ExecutionException {
        Client.execute(new TdApi.SetLogVerbosityLevel(0));
        client = Client.create(this::onUpdate, Throwable::printStackTrace, Throwable::printStackTrace);
        clientAssigned.countDown();
        loggedIn.await();
    }

    /** Called for every new message (incoming and outgoing). */
    public void addMessageListener(Consumer<TdApi.Message> listener) {
        messageListeners.add(listener);
    }

    public TdApi.Chat findPublicChat(String username) throws TelegramException {
        return call(new TdApi.SearchPublicChat(username));
    }

    /** Opens (or creates) the private chat with the given Telegram user id. */
    public TdApi.Chat findChatByUserId(long userId) throws TelegramException {
        return call(new TdApi.CreatePrivateChat(userId, true));
    }

    /** Looks up a user by phone number (international format, e.g. +98912...). */
    public TdApi.User findUserByPhoneNumber(String phoneNumber) throws TelegramException {
        return call(new TdApi.SearchUserByPhoneNumber(phoneNumber, false));
    }

    /** Opens (or creates) the private chat with the owner of the given phone number. */
    public TdApi.Chat findChatByPhoneNumber(String phoneNumber) throws TelegramException {
        TdApi.User user = findUserByPhoneNumber(phoneNumber);
        return findChatByUserId(user.id);
    }

    public TdApi.Chat getChat(long chatId) throws TelegramException {
        return call(new TdApi.GetChat(chatId));
    }

    public TdApi.User getUser(long userId) throws TelegramException {
        return call(new TdApi.GetUser(userId));
    }

    /**
     * Sends a request, waits for the answer, and throws if TDLib returns an error.
     * Never call this from TDLib's own update thread (listeners are safe).
     */
    @SuppressWarnings("unchecked")
    public <T extends TdApi.Object> T call(TdApi.Function<?> request) throws TelegramException {
        CompletableFuture<TdApi.Object> future = new CompletableFuture<>();
        client.send(request, future::complete);

        TdApi.Object result;
        try {
            result = future.get();
        } catch (Exception e) {
            throw new TelegramException(-1, String.valueOf(e.getMessage()));
        }
        if (result instanceof TdApi.Error err) {
            throw new TelegramException(err.code, err.message);
        }
        return (T) result;
    }

    @Override
    public void close() {
        client.send(new TdApi.Close(), null);
    }

    // ---------- updates and login ----------

    private void onUpdate(TdApi.Object object) {
        try {
            clientAssigned.await();
        } catch (InterruptedException e) {
            return;
        }

        if (object instanceof TdApi.UpdateAuthorizationState u) {
            handleAuthState(u.authorizationState);
        } else if (object instanceof TdApi.UpdateNewMessage u) {
            for (Consumer<TdApi.Message> listener : messageListeners) {
                listenerPool.submit(() -> {
                    try {
                        listener.accept(u.message);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        }else if (object instanceof TdApi.UpdateFile
        || object instanceof TdApi.UpdateMessageSendAcknowledged
        || object instanceof TdApi.UpdateMessageSendSucceeded
        || object instanceof TdApi.UpdateMessageSendFailed) {
        for (Consumer<TdApi.Object> listener : updateListeners) {
            listenerPool.submit(() -> {
                try {
                    listener.accept(object);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
    }
    }

    private void authStep(TdApi.AuthorizationState state, TdApi.Function<?> request) {
        client.send(request, result -> {
            if (result instanceof TdApi.Error err) {
                System.err.println("Error: " + err.message);
                Consumer<String> listener = authErrorListener;
                if (listener != null) {
                    listener.accept(err.message);
                }
                handleAuthState(state);
            }
        });
    }

    private void handleAuthState(TdApi.AuthorizationState state) {
        switch (state.getConstructor()) {
            case TdApi.AuthorizationStateWaitTdlibParameters.CONSTRUCTOR -> {
                TdApi.SetTdlibParameters p = new TdApi.SetTdlibParameters();
                p.databaseDirectory = databaseDirectory;
                p.useMessageDatabase = true;
                p.useSecretChats = false;
                p.apiId = apiId;
                p.apiHash = apiHash;
                p.systemLanguageCode = "en";
                p.deviceModel = "Desktop";
                p.applicationVersion = "1.0";
                client.send(p, r -> {
                    if (r instanceof TdApi.Error err) System.err.println("Error: " + err.message);
                });
            }

            case TdApi.AuthorizationStateWaitPhoneNumber.CONSTRUCTOR -> {
                String phone = prompt.ask("Phone number (with country code, e.g. +98912...): ");
                authStep(state, new TdApi.SetAuthenticationPhoneNumber(phone, null));
            }

            case TdApi.AuthorizationStateWaitCode.CONSTRUCTOR -> {
                String code = prompt.ask("Login code sent by Telegram: ");
                authStep(state, new TdApi.CheckAuthenticationCode(code));
            }

            case TdApi.AuthorizationStateWaitPassword.CONSTRUCTOR -> {
                String password = prompt.askRaw("2FA password: ");
                authStep(state, new TdApi.CheckAuthenticationPassword(password));
            }

            case TdApi.AuthorizationStateReady.CONSTRUCTOR -> {
                System.out.println("Logged in.");
                loggedIn.countDown();
            }

            case TdApi.AuthorizationStateClosed.CONSTRUCTOR -> {
                System.out.println("Telegram client closed.");
                // NOTE: never System.exit(0) here. When embedded in another app
                // (e.g. Spring Boot + admin-panel restarts), exiting would kill
                // the whole host process. The owner (runner/service) decides.
            }

            default -> { }
        }
    }
    /** Receives file-progress and send-status updates. */
    public void addUpdateListener(Consumer<TdApi.Object> listener) {
        updateListeners.add(listener);
    }
}
