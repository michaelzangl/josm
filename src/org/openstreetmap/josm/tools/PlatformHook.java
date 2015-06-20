// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.tools;

import java.io.File;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

/**
 * This interface allows platform (operating system) dependent code
 * to be bundled into self-contained classes.
 * @since 1023
 */
public interface PlatformHook {

    /**
      * The preStartupHook will be called extremly early. It is
      * guaranteed to be called before the GUI setup has started.
      *
      * Reason: On OSX we need to inform the Swing libraries
      * that we want to be integrated with the OS before we setup our GUI.
      */
    void preStartupHook();

    /**
      * The afterPrefStartupHook will be called early, but after
      * the preferences have been loaded and basic processing of
      * command line arguments is finished.
      * It is guaranteed to be called before the GUI setup has started.
      */
    void afterPrefStartupHook();

    /**
      * The startupHook will be called early, but after the GUI
      * setup has started.
      *
      * Reason: On OSX we need to register some callbacks with the
      * OS, so we'll receive events from the system menu.
      */
    void startupHook();

    /**
      * The openURL hook will be used to open an URL in the
      * default web browser.
     * @param url The URL to open
     * @throws IOException if any I/O error occurs
      */
    void openUrl(String url) throws IOException;

    /**
      * The initSystemShortcuts hook will be called by the
      * Shortcut class after the modifier groups have been read
      * from the config, but before any shortcuts are read from
      * it or registered from within the application.
      *
      * Plese note that you are not allowed to register any
      * shortuts from this hook, but only "systemCuts"!
      *
      * BTW: SystemCuts should be named "system:&lt;whatever&gt;",
      * and it'd be best if sou'd recycle the names already used
      * by the Windows and OSX hooks. Especially the later has
      * really many of them.
      *
      * You should also register any and all shortcuts that the
      * operation system handles itself to block JOSM from trying
      * to use them---as that would just not work. Call setAutomatic
      * on them to prevent the keyboard preferences from allowing the
      * user to change them.
      */
    void initSystemShortcuts();

    /**
      * The makeTooltip hook will be called whenever a tooltip for
      * a menu or button is created.
      *
      * Tooltips are usually not system dependent, unless the
      * JVM is too dumb to provide correct names for all the keys.
      *
      * Another reason not to use the implementation in the *nix
      * hook are LAFs that don't understand HTML, such as the OSX LAFs.
      *
     * @param name Tooltip text to display
     * @param sc Shortcut associated (to display accelerator between parenthesis)
     * @return Full tooltip text (name + accelerator)
      */
    String makeTooltip(String name, Shortcut sc);

    /**
     * Returns the default LAF to be used on this platform to look almost as a native application.
     * @return The default native LAF for this platform
     */
    String getDefaultStyle();

    /**
     * Determines if the platform allows full-screen.
     * @return {@code true} if full screen is allowed, {@code false} otherwise
     */
    boolean canFullscreen();

    /**
     * Renames a file.
     * @param from Source file
     * @param to Target file
     * @return {@code true} if the file has been renamed, {@code false} otherwise
     */
    boolean rename(File from, File to);

    /**
     * Returns a detailed OS description (at least family + version).
     * @return A detailed OS description.
     * @since 5850
     */
    String getOSDescription();

    /**
     * Setup system keystore to add JOSM HTTPS certificate (for remote control).
     * @param entryAlias The entry alias to use
     * @param trustedCert the JOSM certificate for localhost
     * @return {@code true} if something has changed as a result of the call (certificate installation, etc.)
     * @throws KeyStoreException in case of error
     * @throws IOException in case of error
     * @throws CertificateException in case of error
     * @throws NoSuchAlgorithmException in case of error
     * @since 7343
     */
    boolean setupHttpsCertificate(String entryAlias, KeyStore.TrustedCertificateEntry trustedCert)
            throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException;

    /**
     * Returns the platform-dependent default cache directory.
     * @return the platform-dependent default cache directory
     * @since 7829
     */
    File getDefaultCacheDirectory();

    /**
     * Returns the platform-dependent default preferences directory.
     * @return the platform-dependent default preferences directory
     * @since 7831
     */
    File getDefaultPrefDirectory();

    /**
     * Returns the platform-dependent default user data directory.
     * @return the platform-dependent default user data directory
     * @since 7834
     */
    File getDefaultUserDataDirectory();
}
