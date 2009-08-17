package utilMDE;

import org.tmatesoft.svn.core.wc.*;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.*;
import org.ini4j.Ini;

import java.io.*;
import java.util.*;
import java.net.URL;

/**
 * This program, mvc for Multiple Version Control, lets you run a version
 * control command, such as "status" or "update", on a <b>set</b> of
 * Bzr/CVS/SVN/Hg checkouts rather than just one.<p>
 *
 * This program simplifies managing your checkouts.  You might
 * want to know whether any of them have uncommitted changes, or you
 * might want to update all of them.  Or, when setting up a new account,
 * you might want to check them all out.  This program does any of those
 * tasks.  In particular, it accepts these arguments:
 * <pre>
 *   checkout  -- checks out all repositories
 *   update    -- update all checked out repositories
 *   status    -- show files that are changed but not committed
 *   list      -- list the checkouts that this program is aware of
 * </pre><p>
 *
 * You can specify the set of checkouts for the program to manage, or it
 * can search your directory structure to find all of your checkouts, or
 * both.<p>
 *
 * For usage information, run the program with no arguments.<p>
 *
 * <b>File format for "repositories" file:</b><p>
 *
 * (Note:  because mvc can search for all checkouts in your directory, you

 * The "repositories" file contains a list of sections.  Each section names
 * either a root from which a sub-part (e.g., a module or a subdirectory)
 * will be checked out, or a repository all of which will be checked out.
 * Examples include:
 * <pre>
 * CVSROOT: :ext:login.csail.mit.edu:/afs/csail.mit.edu/u/m/mernst/.CVS/.CVS-mernst
 * SVNROOT: svn+ssh://tricycle.cs.washington.edu/cse/courses/cse403/09sp
 * REPOS: svn+ssh://login.csail.mit.edu/afs/csail/u/a/akiezun/.SVN/papers/parameterization-paper/trunk
 * HGREPOS: https://jsr308-langtools.googlecode.com/hg
 * </pre><p>
 *
 * Within each section is a list of directories that contain a checkout
 * from that repository.  If the section names a root, then a module or
 * subdirectory is needed.  By default, the directory's basename is used.
 * This can be overridden by specifying the module/subdirectory on the same
 * line, after a space.  If the section names a repository, then no module
 * information is needed or used.<p>
 *
 * Here are some example sections:
 * <pre>
 * CVSROOT: :ext:login.csail.mit.edu:/afs/csail.mit.edu/group/pag/projects/classify-tests/.CVS
 * ~/research/testing/symstra-eclat-paper
 * ~/research/testing/symstra-eclat-code
 * ~/research/testing/eclat
 *
 * SVNROOT: svn+ssh://login.csail.mit.edu/afs/csail/group/pag/projects/.SVNREPOS/
 * ~/research/typequals/igj
 * ~/research/typequals/annotations-papers
 *
 * SVNREPOS: svn+ssh://login.csail.mit.edu/afs/csail/group/pag/projects/abb/REPOS
 * ~/prof/grants/2008-06-abb/abb
 *
 * HGREPOS: https://checker-framework.googlecode.com/hg/
 * ~/research/types/checker-framework
 *
 * SVNROOT: svn+ssh://login.csail.mit.edu/afs/csail/u/d/dannydig/REPOS/
 * ~/research/concurrency/concurrentPaper
 * ~/research/concurrency/mit.edu.concurrencyRefactorings concurrencyRefactorings/project/mit.edu.concurrencyRefactorings
 * </pre>
 *
 * Furthermore, these sections have identical effects:
 * <pre>
 * SVNROOT: https://crashma.googlecode.com/svn/
 * ~/research/crashma trunk
 *
 * SVNREPOS: https://crashma.googlecode.com/svn/trunk
 * ~/research/crashma
 * </pre>
 * and, all 3 of these sections have identical effects:
 * <pre>
 * SVNROOT: svn+ssh://login.csail.mit.edu/afs/csail/group/pag/projects/
 * ~/research/typequals/annotations
 *
 * SVNROOT: svn+ssh://login.csail.mit.edu/afs/csail/group/pag/projects/
 * ~/research/typequals/annotations annotations
 *
 * SVNREPOS: svn+ssh://login.csail.mit.edu/afs/csail/group/pag/projects/annotations
 * ~/research/typequals/annotations
 * </pre><p>
 *
 * When performing a checkout, the parent directories are created if
 * needed.<p>
 *
 * In the file, blank lines, and lines beginning with "#", are ignored.<p>
 */

// TODO:

// It might be nice to list all the "unexpected" checkouts -- those found
// on disk that are not in the checkouts file.  This permits the checkouts
// file to be updated and then used in preference to searching the whole
// filesystem, which may be slow.
// You can do this from the command line by comparing the output of these
// two commands:
//   mvc list --repositories /dev/null | sort > checkouts-from-directory
//   mvc list --search=false | sort > checkouts-from-file

// In checkouts file, use of space delimiter for specifyng module interacts
// badly with file names that contain spaces.  This doesn't seem important
// enough to fix.

// When discovering checkouts from a directory structure, there is a
// problem when two modules from the same SVN repository are checked out,
// with one checkout inside the other at the top level.  The inner
// checkout's directory can be mis-reported as the outer one.  This isn't
// always a problem for nested checkouts, and nested checkouts are bad
// style anyway, so I am deferring investigating/fixing it.

public class MultiVersionControl {

  @Option("File with list of checkouts.  Set it to /dev/null to suppress reading.")
  public String checkouts = new File(userHome, ".mvc-checkouts").getPath();

  @Option("Directory under which to search for checkouts; may be supplied multiple times; default=home dir")
  public List<String> dir = new ArrayList<String>();

  @Option("Searches for all checkouts, not just those listed in a file; default=true")
  public boolean search = true;

  // TODO: use consistent names: both "show" or both "print"

  @Option("Display commands as they are executed")
  public boolean show;

  @Option("Print the directory before executing commands")
  public boolean print_directory;

  @Option("Do not execute commands; just print them.  Implies --show --redo-existing")
  public boolean dry_run;

  /**  Default is for checkout command to skip existing directories. */
  @Option("Redo existing checkouts; relevant only to checkout command")
  public boolean redo_existing;

  @Option("Print debugging output")
  public static boolean debug;

  enum Action {
    CHECKOUT,
      STATUS,
      UPDATE,
      LIST
      };
  // Shorter variants
  private Action CHECKOUT = Action.CHECKOUT;
  private Action STATUS = Action.STATUS;
  private Action UPDATE = Action.UPDATE;
  private Action LIST = Action.LIST;

  private Action action;

  @SuppressWarnings("nullness") // user.home property always exists
  static final /*@NonNull*/ String userHome = System.getProperty ("user.home");

  public static void main (String[] args) {
    setupSVNKIT();
    MultiVersionControl mvc = new MultiVersionControl(args);

    Set<Checkout> checkouts = new LinkedHashSet<Checkout>();

    try {
      readCheckouts(new File(mvc.checkouts), checkouts);
    } catch (IOException e) {
      System.err.println("Problem reading file " + mvc.checkouts + ": " + e.getMessage());
    }

    if (mvc.search) {
      for (String adir : mvc.dir) {
        if (debug) {
          System.out.println("Searching for checkouts under " + adir);
        }
        findCheckouts(new File(adir), checkouts);
      }
    }

    mvc.process(checkouts);
  }

  private static void setupSVNKIT() {
    DAVRepositoryFactory.setup();
    SVNRepositoryFactoryImpl.setup();
    FSRepositoryFactory.setup();
  }

  public MultiVersionControl(String[] args) {
    parseArgs(args);
  }

  public void parseArgs(String[] args) /*@Raw*/ {
    Options options = new Options ("mvc [options] {checkout,status,update,list}", this);
    String[] remaining_args = options.parse_or_usage (args);
    if (remaining_args.length != 1) {
      options.print_usage("Please supply exactly one argument (found %d)%n%s", remaining_args.length, UtilMDE.join(remaining_args, " "));
      System.exit(1);
    }
    String action_string = remaining_args[0];
    if ("checkout".startsWith(action_string)) {
      action = CHECKOUT;
    } else if ("status".startsWith(action_string)) {
      action = STATUS;
    } else if ("update".startsWith(action_string)) {
      action = UPDATE;
    } else if ("list".startsWith(action_string)) {
      action = LIST;
    } else {
      options.print_usage("Unrecognized action \"%s\"", action_string);
      assert false;
    }

    // clean up options
    if (dir.isEmpty()) {
      dir.add(userHome);
    }

    if (dry_run) {
      show = true;
      redo_existing = true;
    }

    if (action == CHECKOUT) {
      show = true;
    }
    if (action == UPDATE) {
      print_directory = true;
    }

    if (debug) {
      show = true;
    }

  }

  static enum RepoType {
    BZR,
    CVS,
    HG,
    SVN };

  // TODO: have subclasses of Checkout for the different varieties, perhaps.
  static class Checkout {
    RepoType repoType;
    /** Local directory */
    // actually the parent directory?
    File directory;
    /**
     * Non-null for CVS and SVN.
     * May be null for distributed version control systems (Bzr, Hg).
     * For distributed systems, refers to the parent repository from which
     * this was cloned, not the one here in this directory
     * <p>
     * Most operations don't need this.  it is needed for checkout, though.
     */
    /*@Nullable*/ String repository;
    /**
     * Null if no module, just whole thing.
     * Non-null for CVS and, optionally, for SVN.
     * Null for distributed version control systems (Bzr, Hg).
     */
    /*@Nullable*/ String module;


    Checkout(RepoType repoType, File directory) {
      this(repoType, directory, null, null);
    }

    Checkout(RepoType repoType, File directory, /*@Nullable*/ String repository, /*@Nullable*/ String module) {
      // Directory might not exist if we are running the checkout command.
      // If it exists, it must be a directory.
      assert (directory.exists() ? directory.isDirectory() : true)
        : "Not a directory: " + directory;
      this.repoType = repoType;
      this.directory = directory;
      this.repository = repository;
      this.module = module;
      // These asserts come at the end so that the error message can be better.
      switch (repoType) {
      case BZR:
        assert (directory.exists() ? new File(directory, ".bzr").isDirectory() : true);
        assert module == null;
        break;
      case CVS:
        assert (directory.exists() ? new File(directory, "CVS").isDirectory() : true) : "Bad CVS: " + this;
        assert module != null : "Bad CVS: " + this;
        break;
      case HG:
        assert (directory.exists() ? new File(directory, ".hg").isDirectory() : true);
        assert module == null;
        break;
      case SVN:
        assert (directory.exists() ? new File(directory, ".svn").isDirectory() : true) : "Bad SVN: " + this;
        assert module == null : "Bad SVN: " + this;
        break;
      default:
        assert false;
      }
    }

    @Override
    public boolean equals(/*@Nullable*/ Object other) {
      if (! (other instanceof Checkout))
        return false;
      Checkout c2 = (Checkout) other;
      return ((repoType == c2.repoType)
              && directory.equals(c2.directory)
              && ((repository == null)
                  ? (repository == c2.repository)
                  : repository.equals(c2.repository))
              && ((module == null)
                  ? (module == c2.module)
                  : module.equals(c2.module)));
    }

    @Override
    public int hashCode() {
      return (repoType.hashCode()
              + directory.hashCode()
              + (repository == null ? 0 : repository.hashCode())
              + (module == null ? 0 : module.hashCode()));
    }

    @Override
      public String toString() {
      return repoType
        + " " + directory
        + " " + repository
        + " " + module;
    }

  }


  ///////////////////////////////////////////////////////////////////////////
  /// Read checkouts from a file
  ///

  static void readCheckouts(File file, Set<Checkout> checkouts) throws IOException {
    RepoType currentType = RepoType.BZR; // arbitrary choice
    String currentRoot = null;
    boolean currentRootIsRepos = false;

    EntryReader er = new EntryReader(file);
    for (String line : er) {
      if (debug) {
        System.out.println("line: " + line);
      }
      line = line.trim();
      // Skip comments and blank lines
      if (line.equals("") || line.startsWith("#")) {
        continue;
      }

      String[] splitTwo = line.split("[ \t]+");
      if (debug) {
        System.out.println("split length: " + splitTwo.length);
      }
      if (splitTwo.length == 2) {
        String word1 = splitTwo[0];
        String word2 = splitTwo[1];
        if (word1.equals("BZRROOT:") || word1.equals("BZRREPOS:")) {
          currentType = RepoType.BZR;
          currentRoot = word2;
          currentRootIsRepos = word1.equals("BZRREPOS:");
          continue;
        } else if (word1.equals("CVSROOT:")) {
          currentType = RepoType.CVS;
          currentRoot = word2;
          currentRootIsRepos = false;
          // If the CVSROOT is remote, try to make it local.
          if (currentRoot.startsWith(":ext:")) {
            String[] rootWords = currentRoot.split(":");
            String possibleRoot = rootWords[rootWords.length-1];
            if (new File(possibleRoot).isDirectory()) {
              currentRoot = possibleRoot;
            }
          }
          continue;
        } else if (word1.equals("HGROOT:") || word1.equals("HGREPOS:")) {
          currentType = RepoType.HG;
          currentRoot = word2;
          currentRootIsRepos = word1.equals("HGREPOS:");
          continue;
        } else if (word1.equals("SVNROOT:") || word1.equals("SVNREPOS:")) {
          currentType = RepoType.SVN;
          currentRoot = word2;
          currentRootIsRepos = word1.equals("SVNREPOS:");
          continue;
        }
      }

      if (currentRoot == null) {
        System.err.printf("need root before directory at line %d of file %s%n",
                          er.getLineNumber(), er.getFileName());
        System.exit(1);
      }

      // Replace "~" by "$HOME", because -d (and Athena's "cd" command) does not
      // understand ~, but it does understand $HOME.
      String dirname;
      String root = currentRoot;
      if (root.endsWith("/")) root = root.substring(0,root.length()-1);
      String module = null;

      int spacePos = line.lastIndexOf(' ');
      if (spacePos == -1) {
        dirname = line;
      } else {
        dirname = line.substring(0, spacePos);
        module = line.substring(spacePos+1);
      }

      // The directory may not yet exist if we are doing a checkout.
      File dir = new File(dirname.replaceFirst("^~", userHome));

      if (module == null) {
          module = dir.getName();
      }
      if (currentType != RepoType.CVS) {
        if (! currentRootIsRepos) {
          root = root + "/" + module;
        }
        module = null;
      }

      checkouts.add(new Checkout(currentType, dir, root, module));
    }
  }


  ///////////////////////////////////////////////////////////////////////////
  /// Find checkouts in a directory
  ///

  /// Note:  this can be slow, because it examines every directory in your
  /// entire home directory.

  /**
   * Find checkouts.  These are indicated by directories named .bzr, CVS,
   * .hg, or .svn.
   * <p>
   *
   * With some version control systems, this task is easy:  there is
   * exactly one .bzr or .hg directory per checkout.  With CVS and SVN,
   * there is one CVS/.svn directory per directory of the checkout.  It is
   * permitted for one checkout to be made inside another one (though that
   * is bad style), so we must examine every CVS/.svn directory to find all
   * the distinct checkouts.
   */

  // An alternative implementation would use Files.walkFileTree, but that
  // is available only in Java 7.



  /** Accept only directories that are not symbolic links. */
  static class IsDirectoryFilter implements FileFilter {
    public boolean accept(File pathname) {
      try {
      return pathname.isDirectory()
        && pathname.getPath().equals(pathname.getCanonicalPath());
      } catch (IOException e) {
        throw new Error(e);
        // return false;
      }
    }
  }

  static IsDirectoryFilter idf = new IsDirectoryFilter();

  /** Find all checkouts under the given directory. */
  static Set<Checkout> findCheckouts(File dir) {
    assert dir.isDirectory();

    Set<Checkout> checkouts = new LinkedHashSet<Checkout>();

    findCheckouts(dir, checkouts);

    return checkouts;
  }


  /**
   * Find all checkouts at or under the given directory (or, as a special
   * case, also its parent -- could rewrite to avoid that case), and adds
   * them to checkouts.  Works by checking whether dir or any of its
   * descendants is a version control directory.
   */
  private static void findCheckouts(File dir, Set<Checkout> checkouts) {
    assert dir.isDirectory();

    String dirName = dir.getName().toString();
    File parent = dir.getParentFile();
    if (parent != null) {
      if (dirName.equals(".bzr")) {
        checkouts.add(new Checkout(RepoType.BZR, parent, null, null));
      } else if (dirName.equals("CVS")) {
        addCheckoutCvs(dir, parent, checkouts);
      } else if (dirName.equals(".hg")) {
        checkouts.add(dirToCheckoutHg(dir, parent));
      } else if (dirName.equals(".svn")) {
        checkouts.add(dirToCheckoutSvn(parent));
      }
    }

    @SuppressWarnings("nullness") // listFiles => non-null because dir is a directory
    File /*@NonNull*/ [] childdirs = dir.listFiles(idf);
    for (File childdir : childdirs) {
      findCheckouts(childdir, checkouts);
    }
  }


  /**
   * Given a directory named "CVS" , create a corresponding Checkout object
   * for its parent.  Returns null if this directory is named "CVS" but is
   * not a version control directory.  (Google Web Toolkit does that, for
   * example.)
   */
  static void addCheckoutCvs(File cvsDir, File dir, Set<Checkout> checkouts) {
    assert cvsDir.getName().toString().equals("CVS") : cvsDir.getName();
    // relative path within repository
    File repositoryFile = new File(cvsDir, "Repository");
    File rootFile = new File(cvsDir, "Root");
    if (! (repositoryFile.exists() && rootFile.exists())) {
      // apparently it wasn't a version control directory
      return;
    }
    String pathInRepo = UtilMDE.readFile(repositoryFile).trim();
    String repoRoot = UtilMDE.readFile(rootFile).trim();
    /*@NonNull*/ File repoFileRoot = new File(pathInRepo);
    while (repoFileRoot.getParentFile() != null) {
      @SuppressWarnings("nullness") // just checed that parent is non-null
      /*@NonNull*/ File newRepoFileRoot = repoFileRoot.getParentFile();
      repoFileRoot = newRepoFileRoot;
    }

    // strip common suffix off of local dir and repo url
    Pair<File,File> stripped = removeCommonSuffixDirs(dir,
                                                      new File(pathInRepo),
                                                      repoFileRoot,
                                                      "CVS");
    dir = stripped.a;
    String pathInRepoAtCheckout;
    if (stripped.b != null) {
      pathInRepoAtCheckout = stripped.b.toString();
    } else {
      pathInRepoAtCheckout = dir.getName();
    }

    checkouts.add(new Checkout(RepoType.CVS, dir, repoRoot, pathInRepoAtCheckout));
  }

  /**
   * Given a directory named ".hg" , create a corresponding Checkout object
   * for its parent.
   */
  static Checkout dirToCheckoutHg(File hgDir, File dir) {
    String repository = null;

    File hgrcFile = new File(hgDir, "hgrc");
    Ini ini;
    // There also exist Hg commands that will do this same thing.
    if (hgrcFile.exists()) {
      try {
        ini = new Ini(new FileReader(hgrcFile));
      } catch (IOException e) {
        throw new Error("Problem reading file " + hgrcFile);
      }

      Ini.Section pathsSection = ini.get("paths");
      if (pathsSection != null) {
        repository = pathsSection.get("default");
        if (repository != null && repository.endsWith("/")) {
          repository = repository.substring(0, repository.length()-1);
        }
      }
    }

    return new Checkout(RepoType.HG, dir, repository, null);
  }


  /**
   * Given a directory that contains a .svn subdirectory, create a
   * corresponding Checkout object.
   */
  static Checkout dirToCheckoutSvn(File dir) {

    // For SVN, do
    //   svn info
    // and grep out these lines:
    //   URL: svn+ssh://login.csail.mit.edu/afs/csail/group/pag/projects/reCrash/repository/trunk/www
    //   Repository Root: svn+ssh://login.csail.mit.edu/afs/csail/group/pag/projects/reCrash/repository

    // Use SVNKit?
    // Con: introduces dependency on external library.
    // Pro: no need to re-implement or to call external process (which
    //   might be slow for large checkouts).

    @SuppressWarnings("nullness") // SVNKit is not yet annotated
    SVNWCClient wcClient = new SVNWCClient((/*@Nullable*/ ISVNAuthenticationManager) null, null);
    SVNInfo info;
    try {
      info = wcClient.doInfo(new File(dir.toString()), SVNRevision.WORKING);
    } catch (SVNException e) {
      throw new Error(e);
    }
    // getFile is null when operating on a working copy, as I am
    // String relativeFile = info.getPath(); // relative to repository root -- can use to determine root of checkout
    // getFile is just the (absolute) local file name for local items -- same as "dir"
    // File relativeFile = info.getFile();
    SVNURL url = info.getURL();
    SVNURL repoRoot = info.getRepositoryRootURL();
    if (debug) {
      System.out.println();
      System.out.println("repoRoot = " + repoRoot);
      System.out.println(" repoUrl = " + url);
      System.out.println("     dir = " + dir.toString());
    }

    // Strip common suffix off of local dir and repo url.
    Pair<File,File> stripped = removeCommonSuffixDirs(dir,
                                                      new File(url.getPath()),
                                                      new File(repoRoot.getPath()),
                                                      ".svn");
    dir = stripped.a;
    try {
      url = url.setPath(stripped.b.toString(), false);
    } catch (SVNException e) {
      throw new Error(e);
    }

    if (debug) {
      System.out.println("stripped: " + stripped);
      System.out.println("repoRoot = " + repoRoot);
      System.out.println(" repoUrl = " + url);
      System.out.println("     dir = " + dir.toString());
    }

    assert url.toString().startsWith(repoRoot.toString())
      : "repoRoot="+repoRoot+", url="+url;
    return new Checkout(RepoType.SVN, dir, url.toString(), null);

    /// Old implementation
    // String module = url.toString().substring(repoRoot.toString().length());
    // if (module.startsWith("/")) {
    //   module = module.substring(1);
    // }
    // if (module.equals("")) {
    //   module = null;
    // }
    // return new Checkout(RepoType.SVN, dir, repoRoot.toString(), module);



  }

  /**
   * Strip identical elements off the end of both paths, and then return
   * what is left of each.  Returned elements can be null!  If p2_limit is
   * non-null, then it should be a parent of p2, and the stripping stops
   * when p2 becomes p2_limit.  If p1_contains is non-null, then p1 must
   * contain a subdirectory of that name.
   */
  static Pair<File,File> removeCommonSuffixDirs(File p1, File p2, File p2_limit, String p1_contains) {
    if (debug) {
      System.out.printf("removeCommonSuffixDirs(%s, %s, %s, %s)%n", p1, p2, p2_limit, p1_contains);
    }
    // new names for results, because we will be side-effecting them
    /*@Nullable*/ File r1 = p1;
    /*@Nullable*/ File r2 = p2;
    while (r1 != null
           && r2 != null
           && (p2_limit == null || ! r2.equals(p2_limit))
           && r1.getName().equals(r2.getName())) {
      if (p1_contains != null
          && ! new File(r1.getParentFile(), p1_contains).isDirectory()) {
        break;
      }
      r1 = r1.getParentFile();
      r2 = r2.getParentFile();
    }
    if (debug) {
      System.out.printf("removeCommonSuffixDirs => %s %s%n", r1, r2);
    }
    return new Pair<File,File>(r1,r2);
  }


  ///////////////////////////////////////////////////////////////////////////
  /// Process checkouts
  ///

  public void process(Set<Checkout> checkouts) {
    String repo;

    ProcessBuilder pb = new ProcessBuilder("");
    pb.redirectErrorStream(true);
    String filter;              // needs to be a regexp!


    CHECKOUTLOOP:
    for (Checkout c : checkouts) {
      if (debug) {
        System.out.println(c);
      }
      File dir = c.directory;

      filter = null;

      // Set pb.command() to be the command to be executed.
      switch (action) {
      case LIST:
        System.out.println(c);
        break;
      case CHECKOUT:
        pb.directory(dir.getParentFile());
        String dirbase = dir.getName();
        if (c.repository == null) {
          System.out.printf("Skipping checkout with unknown repository:%n  %s%n",
                            dir);
          continue CHECKOUTLOOP;
        }
        switch (c.repoType) {
        case BZR:
          throw new Error("not yet implemented");
          // break;
        case CVS:
          assert c.module != null : "@SuppressWarnings(nullness): dependent type CVS";
          pb.command("cvs", "-d", c.repository, "checkout",
                     "-P", // prune empty directories
                     "-ko", // no keyword substitution
                     c.module);
          break;
        case HG:
          pb.command("hg", "clone", c.repository, dirbase);
          break;
        case SVN:
          pb.command("svn", "checkout", c.repository, dirbase);
          break;
        default:
          assert false;
        }
        break;
      case STATUS:
        switch (c.repoType) {
        case BZR:
          throw new Error("not yet implemented");
          // break;
        case CVS:
          assert c.repository != null;
          pb.command("cvs", "-d", c.repository, "diff",
                     "-b",      // compress whitespace
                     "--brief", // report only whether files differ, not details
                     "-N");     // report new files
//         # For the last perl command, this also works:
//         #   perl -p -e 'chomp(\$cwd = `pwd`); s/^Index: /\$cwd\\//'";
//         # but the one we use is briefer and uses the abbreviated directory name.
//         $filter = "grep -v \"unrecognized keyword 'UseNewInfoFmtStrings'\" | grep \"^Index:\" | perl -p -e 's|^Index: |$dir\\/|'";
          break;
        case HG:
          pb.command("hg", "status");
          // also do
          //   hg outgoing -l 1
          // and the third line is either "no changes found" or not
          break;
        case SVN:
          pb.command("svn", "status");
          // Alternate implementation using diff (by analogy with CVS).  But
          // for SVN, "status" is the appropriate command.
          // "svn diff --summarize" and "svn log -vq" can only compare one
          // repository to another, but I want to compare the working
          // directory to the repository.
          // pb.command("svn", "diff", "--diff-cmd", "diff", "-x", "-q", "-x", "-r", "-x", "-N");
//         $filter = "grep \"^Index:\" | perl -p -e 's|^Index: |$dir\\/|'";
          break;
        default:
          assert false;
        }
        break;
      case UPDATE:
        switch (c.repoType) {
        case BZR:
          throw new Error("not yet implemented");
          // break;
        case CVS:
          assert c.repository != null;
          break;
        case HG:
          break;
        case SVN:
          assert c.repository != null;
          break;
        default:
          assert false;
        }
        // ...
//       if (defined($cvsroot)) {
//         $command = "$cvs -d $cvsroot -Q update -d";
//         $filter = "grep -v \"config: unrecognized keyword 'UseNewInfoFmtStrings'\"";
//       } else {
//         $command = "svn -q update";
//         $filter = "grep -v \"Killed by signal 15.\"";
//       }
        // ...
        break;
      default:
        assert false;
      }

      // Check that the directory exists (OK if it doesn't for checkout).
      if (debug) {
        System.out.println(dir + ":");
      }
      if (dir.exists()) {
        if (action == CHECKOUT && ! redo_existing) {
          System.out.println("Skipping checkout (dir already exists): " + dir);
          continue;
        }
      } else {
        // Directory does not exist
        File parent = dir.getParentFile();
        if (parent == null) {
          System.err.println("Root directory cannot be a checkout");
          System.exit(1);
        }
        switch (action) {
        case LIST:
          // nothing to do
          break;
        case CHECKOUT:
          if (! parent.exists()) {
            if (show) {
              System.out.println("Directory does not exist"
                                 + (dry_run ? "" : " (creating)")
                                 + ": parent");
            }
            if (! dry_run) {
              if (! parent.mkdirs()) {
                System.err.println("Could not create directory: " + parent);
                System.exit(1);
              }
            }
          }
          break;
        case STATUS:
        case UPDATE:
          System.out.println("Cannot find directory: " + dir);
          continue;
        default:
          assert false;
        }
      }

//     # Show the command.
//     if ($show) {
//       if (($action eq "checkout")
//           # Better would be to change the printed (but not executed) command
//           # || (($action eq "update") && defined($svnroot))
//           || ($action eq "update")) {
//         print "cd $command_cwd\n";
//       }
//       print "command: $command\n";
//     }
//
//     # Perform the command
//     if (! $dry_run) {
//       my $tmpfile = "/tmp/cmd-output-$$";
//       # For debugging
//       # my $command_cwd_sanitized = $command_cwd;
//       # $command_cwd_sanitized =~ s/\//_/g;
//       # my $tmpfile = "/tmp/cmd-output-$$-$command_cwd_sanitized";
//       my $command_redirected = "$command > $tmpfile 2>&1";
//       if ($debug) { print "About to execute: $command_redirected\n"; }
//       my $result = system("$command_redirected");
//       if ($debug) { print "Executed: $command_redirected\n"; }
//       if ($debug) { print "raw result = $result\n"; }
//       if ($result == -1) {
//         print "failed to execute: $command_redirected: $!\n";
//       } elsif ($result & 127) {
//         printf "child died with signal %d, %s coredump\n",
//         ($result & 127),  ($result & 128) ? 'with' : 'without';
//       } else {
//         # Problem:  diff returns failure status if there were differences
//         # or if there was an error, so ther's no good way to detect errors.
//         $result = $result >> 8;
//         if ($debug) { print "shifted result = $result\n"; }
//         if ((($action eq "status") && ($result != 0) && ($result != 1))
//             || (($action ne "status") && ($result != 0))) {
//           print "exit status $result for:\n  cd $command_cwd;\n  $command_redirected\n";
//           system("cat $tmpfile");
//         }
//       }
//       # Filter the output
//       if (defined($filter)) {
//         system("cat $tmpfile | $filter > $tmpfile-2");
//         rename("$tmpfile-2", "$tmpfile");
//       }
//       if ($debug && $show_directory) {
//         print "show-directory: $dir:\n";
//         printf "tmpfile size: %d, zeroness: %d, non-zeroness %d\n", (-s $tmpfile), (-z $tmpfile), (! -z $tmpfile);
//       }
//       if ((! -z $tmpfile) && $show_directory) {
//         print "$dir:\n";
//       }
//       system("cat $tmpfile");
//       unlink($tmpfile);
//     }
//     next;
//   }
// }

    }
  }


}