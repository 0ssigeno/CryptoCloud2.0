package Management.Cloud;

import Execution.Main;
import Management.User;
import com.dropbox.core.*;
import com.dropbox.core.http.StandardHttpRequestor;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FolderMetadata;
import com.dropbox.core.v2.files.WriteMode;
import com.dropbox.core.v2.sharing.AccessLevel;
import com.dropbox.core.v2.sharing.AddMember;
import com.dropbox.core.v2.sharing.MemberSelector;
import com.dropbox.core.v2.sharing.SharedFolderMetadata;

import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;


public class Dropbox {
	private static final String KEY_INFO = null;
	private static final String SECRET_INFO = null;
	public final static Path BASE = Paths.get("/");
	private static final long CHUNKED_UPLOAD_CHUNK_SIZE = 8L << 20; // 8MiB
	public final static Path SYSTEM = Paths.get("/System");
	public final static Path GROUPS_COMPOSITION = SYSTEM.resolve("GroupsComposition");
	public final static Path PUBLIC_KEYS = SYSTEM.resolve("PublicKeys");
	public final static Path SIGNED_GROUPS = SYSTEM.resolve("SignedGroups");
	public final static Path MESSAGE_PASSING = SYSTEM.resolve("MessagePassing");
	public final static Path SIGNED_PUBLIC_KEYS = Paths.get("/SignedKeys");
	public final static Path SIGNED_GROUPS_OWNER = Paths.get("/SignedGroupsOwner");
	private static final int CHUNKED_UPLOAD_MAX_ATTEMPTS = 5;

	private static String callerEmail;
	private static DbxClientV2 client;
	private static DbxClientV2 longpollClient;

	/**
	 * Create a DbxClientV2: if is the first time that the user authenticate himself,
	 * he will be redirect on the dropbox web page for the authorization.
	 * After the authorization the token will be saved in DropBoxToken.json
	 * If he already configured his account, the token will be only withdrawn.
	 */
	public static void initDropboxClient() {
		if (client == null) {
			Path url = Main.MY_PERSONAL_PATH.resolve("DropBoxToken.json").toAbsolutePath();
			DbxRequestConfig requestConfig = new DbxRequestConfig("CryptoClouds");

			DbxAuthInfo authInfo = null;
			File file = new File(url.toString());
			if (file.exists()) {
				try {
					authInfo = DbxAuthInfo.Reader.readFromFile(url.toString());
					client = new DbxClientV2(requestConfig, authInfo.getAccessToken(), authInfo.getHost());

				} catch (Exception e) {
					System.out.println("You are not authenticated,redirecting ");

				}
			} else {
				//noinspection ConstantConditions
				if ((KEY_INFO == null) && (SECRET_INFO == null)) {
					System.err.println("Please insert the value of your dropbox key and secret in 'Values'");
					System.exit(1);
				}
				DbxAppInfo appInfo = new DbxAppInfo(KEY_INFO, SECRET_INFO);
				DbxWebAuth webAuth = new DbxWebAuth(requestConfig, appInfo);
				DbxWebAuth.Request webAuthRequest = DbxWebAuth.newRequestBuilder()
						.withNoRedirect()
						.build();

				String authorizeUrl = webAuth.authorize(webAuthRequest);
				System.out.println("1. Go to " + authorizeUrl);
				System.out.println("2. Click \"Allow\" (you might have to log in first).");
				openWebPage(authorizeUrl);
				System.out.println("3. Copy the authorization code.");
				System.out.print("Enter the authorization code here: ");
				String code = Main.input();

				DbxAuthFinish authFinish;

				try {
					authFinish = webAuth.finishFromCode(code);
				} catch (DbxException ex) {
					System.err.println("Error in DbxWebAuth.authorize: " + ex.getMessage());
					System.exit(1);
					return;
				}

				System.out.println("Authorization complete.");
				System.out.println("- User ID: " + authFinish.getUserId());
				System.out.println("- Account ID: " + authFinish.getAccountId());
				System.out.println("- Access Token: " + authFinish.getAccessToken());


				client = new DbxClientV2(requestConfig, authFinish.getAccessToken());
				// Save auth information to output file.
				authInfo = new DbxAuthInfo(authFinish.getAccessToken(), appInfo.getHost());
				try {
					File output = new File(url.toString());
					//noinspection ResultOfMethodCallIgnored
					output.getParentFile().mkdirs();
					DbxAuthInfo.Writer.writeToFile(authInfo, output);
					System.out.println("Saved authorization information to \""
							+ output.getCanonicalPath() + "\".");
				} catch (IOException ex) {
					System.err.println("Error saving to <auth-file-out>: " + ex.getMessage());
					System.err.println("Dumping to stderr instead:");
					//DbxAuthInfo.Writer.writeToStream(authInfo, System.err);
					System.exit(1);
				}
			}
			StandardHttpRequestor.Config config = StandardHttpRequestor.Config.DEFAULT_INSTANCE;
			StandardHttpRequestor.Config longpollConfig = config.copy()
					.withReadTimeout(5, TimeUnit.MINUTES)
					.build();
			StandardHttpRequestor requestor = new StandardHttpRequestor(longpollConfig);
			DbxRequestConfig requestConfigLong = DbxRequestConfig.newBuilder("CryptoClouds longpoll")
					.withHttpRequestor(requestor)
					.build();
			assert authInfo != null;
			longpollClient = new DbxClientV2(requestConfigLong, authInfo.getAccessToken(), authInfo.getHost());
		}
	}

	private static void openWebPage(String url) {
		Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
		if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
			try {
				desktop.browse(URI.create(url));
			} catch (Exception e) {
				throw new Main.ExecutionException("openWebPage", e);

			}
		}
	}

	/**
	 * @return a DbxClientV2 client already initialized
	 */
	public static DbxClientV2 getClient() {
		if (client == null) {
			throw new IllegalStateException("Client not initialized.");
		}
		return client;
	}

	/**
	 * @return a DbxClientV2 client already initialized used for polling
	 */
	public static DbxClientV2 getLongpollClient() {
		if (longpollClient == null) {
			throw new IllegalStateException("LongpollClient not initialized.");
		}
		return longpollClient;
	}

	public static String getCallerEmail() {
		if (callerEmail == null) {
			try {
				callerEmail = Dropbox.getClient().users().getCurrentAccount().getEmail();
			} catch (DbxException e) {
				throw new IllegalStateException("Email not initialized.", e);
			}
		}
		return callerEmail;
	}


	public static Path download(Path initialPath, String nameFile, String extension)
			throws IOException, DbxException {
		Path localPath = Main.MY_TEMP_PATH.resolve(nameFile + extension);
		FileOutputStream out = new FileOutputStream(localPath.toFile());
		client.files().downloadBuilder(initialPath.resolve(nameFile + extension).toString()).download(out);
		return localPath;
	}

	public static void upload(Path localPath, Path uploadedPath) throws IOException, DbxException {

		long size = Files.size(localPath);

		if (size >= CHUNKED_UPLOAD_CHUNK_SIZE) {
			System.err.println("File too big, uploading the file in chunks.");
			//chunkedPersonalUploadFile(localFile, dropboxPathNoName); TODO
		} else {
			InputStream in = Files.newInputStream(localPath);
			client.files().uploadBuilder(uploadedPath.toString())
					.withMode(WriteMode.OVERWRITE)
					.uploadAndFinish(in);

		}

	}

	public static Boolean existFile(Path path) {
		try {
			client.files().getMetadata(path.toString());
			return true;
		}catch (DbxException e){
			return false;
		}

	}


	public static String getSharedFolderId(Path path) throws DbxException {
		return ((FolderMetadata) client.files().getMetadata(path.toString())).getSharedFolderId();
	}

	public static boolean isAdmin() { //1 true, 0 false, -1 non esiste file
		try {
			if (client.sharing().listMountableFolders().getEntries().stream().anyMatch(folder -> folder.getName().equals("System"))) {
				//you obtained the shared filesystem in some way
				try {
					client.files().getMetadata(Dropbox.SYSTEM.toString());
				} catch (DbxException e) {
					//if is not mounted -> user
					return false;
				}
				//you own it -> admin, you don't own it -> user
				return client.sharing().getFolderMetadata(Dropbox.getSharedFolderId(Dropbox.SYSTEM))
						.getAccessType().compareTo(AccessLevel.OWNER) == 0;
			} else {
				//not shared --> Admin
				return true;
			}
		} catch (DbxException e) {
			throw new Main.ExecutionException("isAdmin", e);
		}
	}


	public static void mountFolder(Path nameFolder) throws DbxException {

		List<SharedFolderMetadata> result = client.sharing().listMountableFoldersBuilder().start().getEntries();
		for (SharedFolderMetadata x : result) {
			if (x.getName().equals(nameFolder.toString())) {
				if (x.getPathLower() == null) {
					client.sharing().mountFolder(x.getSharedFolderId());
				}
			}
		}

	}

	public static void createFolder(Path path, Boolean shared) {
		try {
			client.files().getMetadata(path.toString());
		} catch (DbxException e){
			try {
				client.files().createFolderV2(path.toString(), false);
				if(shared){
					client.sharing().shareFolder(path.toString());

				}

			} catch (DbxException e1) {
				throw new Main.ExecutionException("createFolder",e);

			}
		}

	}

	public static void addUsersToFolder(AccessLevel accessLevel, Path path, List<User> users)
			throws DbxException {
		if (client.files().getMetadata(path.toString()).toString().contains("shared_folder_id")) {
			List<AddMember> newAddMembers = new ArrayList<>();
			users.forEach(user ->
					newAddMembers.add(new AddMember(MemberSelector.email(user.getEmail()), accessLevel)));
			client.sharing().addFolderMember(getSharedFolderId(path), newAddMembers);
		}else{
			throw new Main.ExecutionException("addUsersToFolder");

		}

	}

	public static void removeUsersFromFolder(Path path, List<User> users) throws DbxException {
		if (client.files().getMetadata(path.toString()).toString().contains("shared_folder_id")) {
			users.forEach(user -> {
				try {
					client.sharing().removeFolderMember(getSharedFolderId(path),
							MemberSelector.email(user.getEmail()),false);
				} catch (DbxException e) {
					throw new Main.ExecutionException("removeUsersFromFile",e);
				}
			});

		}else{
			throw new Main.ExecutionException("removeUsersFromFolder");

		}
	}
}