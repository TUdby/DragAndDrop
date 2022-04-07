package DragAndDrop;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

//import javax.swing.event.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
//import java.awt.event.*;
import java.io.*;
import java.nio.file.*;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.model.IllegalBucketNameException;

public class DragDropFiles extends JFrame {

	private DefaultListModel model = new DefaultListModel();
	private int count = 0;
	private JTree tree;
	private JLabel label;
	private JButton download;
	private JButton delete;
	private JButton create_bucket;
	private DefaultTreeModel treeModel;
	private TreePath namesPath;
	private JPanel wrap;
	private TreePath selected_path = null;
	private DefaultMutableTreeNode root;

	AmazonS3 s3 = AmazonS3ClientBuilder.standard().withRegion(Regions.US_EAST_1).build();

	private DefaultTreeModel getDefaultTreeModel(AmazonS3 s3) {
		root = new DefaultMutableTreeNode("All My Buckets");

		try {
			for (Bucket bucket : s3.listBuckets()) {
				DefaultMutableTreeNode bucketNode = new DefaultMutableTreeNode(bucket.getName());
				root.add(bucketNode);
				ObjectListing objectListing = s3.listObjects(new ListObjectsRequest().withBucketName(bucket.getName()));
				for (S3ObjectSummary objectSummary : objectListing.getObjectSummaries()) {
					bucketNode.add(new DefaultMutableTreeNode(objectSummary.getKey()));
				}

			}
		} catch (AmazonServiceException ase) {
			System.out.println("Caught an AmazonServiceException, which means your request made it "
					+ "to Amazon S3, but was rejected with an error response for some reason.");
			System.out.println("Error Message:    " + ase.getMessage());
			System.out.println("HTTP Status Code: " + ase.getStatusCode());
			System.out.println("AWS Error Code:   " + ase.getErrorCode());
			System.out.println("Error Type:       " + ase.getErrorType());
			System.out.println("Request ID:       " + ase.getRequestId());
		} catch (AmazonClientException ace) {
			System.out.println("Caught an AmazonClientException, which means the client encountered "
					+ "a serious internal problem while trying to communicate with S3, "
					+ "such as not being able to access the network.");
			System.out.println("Error Message: " + ace.getMessage());
		}

		return new DefaultTreeModel(root);
	}

	public DragDropFiles() {
		super("Drag and Drop File Transfers in Cloud");

		treeModel = getDefaultTreeModel(s3);

		tree = new JTree(treeModel);
		tree.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
		tree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
		tree.setDropMode(DropMode.ON);
		tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		namesPath = tree.getPathForRow(2);
		tree.expandRow(2);
		tree.expandRow(1);
		tree.setRowHeight(0);

		// Handles the tree node selection event that triggered by user selection
		// Identify which tree node(file name) has been selected, for downloading.
		// For more info, see TreeSelectionListener Class in Java
		tree.addTreeSelectionListener(new TreeSelectionListener() {
			public void valueChanged(TreeSelectionEvent e) {
				selected_path = e.getNewLeadSelectionPath();
			}
		});

		tree.setTransferHandler(new TransferHandler() {

			public boolean canImport(TransferHandler.TransferSupport info) {
				// we'll only support drops (not clip-board paste)
				if (!info.isDrop()) {
					return false;
				}
				info.setDropAction(COPY); // Tony added
				info.setShowDropLocation(true);
				// we import Strings and files
				if (!info.isDataFlavorSupported(DataFlavor.stringFlavor)
						&& !info.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
					return false;
				}

				// fetch the drop location
				JTree.DropLocation dl = (JTree.DropLocation) info.getDropLocation();
				TreePath path = dl.getPath();

				// we don't support invalid paths or descendants of the names folder
				if (path == null || namesPath.isDescendant(path)) {
					return false;
				}
				return true;
			}

			public boolean importData(TransferHandler.TransferSupport info) {
				// if we can't handle the import, say so
				if (!canImport(info)) {
					return false;
				}
				// fetch the drop location
				JTree.DropLocation dl = (JTree.DropLocation) info.getDropLocation();

				// fetch the path and child index from the drop location
				TreePath path = dl.getPath();
				int childIndex = dl.getChildIndex();

				// fetch the data and bail if this fails
				String uploadName = "";

				Transferable t = info.getTransferable();
				try {
					java.util.List<File> l = (java.util.List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);

					for (File f : l) {
						uploadName = f.getName();
						Object[] p = path.getPath();

						s3.putObject(new PutObjectRequest(p[1].toString(), uploadName, f));

						break;// We process only one dropped file.
					}
				} catch (UnsupportedFlavorException e) {
					return false;
				} catch (IOException e) {
					return false;
				}

				// if child index is -1, the drop was on top of the path, so we'll
				// treat it as inserting at the end of that path's list of children
				if (childIndex == -1) {
					childIndex = tree.getModel().getChildCount(path.getLastPathComponent());
				}

				// create a new node to represent the data and insert it into the model
				DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(uploadName);
				DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) path.getLastPathComponent();
				treeModel.insertNodeInto(newNode, parentNode, childIndex);

				// make the new node visible and scroll so that it's visible
				tree.makeVisible(path.pathByAddingChild(newNode));
				tree.scrollRectToVisible(tree.getPathBounds(path.pathByAddingChild(newNode)));

				// Display uploading status
				label.setText("UpLoaded **" + uploadName + "** successfully!");

				return true;
			}

		});

		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
		this.wrap = new JPanel();
		this.label = new JLabel("Status Bar...");
		wrap.add(this.label);
		p.add(Box.createHorizontalStrut(4));
		p.add(Box.createGlue());
		p.add(wrap);
		p.add(Box.createGlue());
		p.add(Box.createHorizontalStrut(4));
		getContentPane().add(p, BorderLayout.NORTH);

		getContentPane().add(new JScrollPane(tree), BorderLayout.CENTER);
		download = new JButton("Download");
		download.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (selected_path != null) {
					Object[] element = selected_path.getPath();
					String bucketName = element[1].toString();
					String key = element[2].toString();

					JOptionPane.showMessageDialog(null,
							"You are downloading the file (" + key + ") from bucket (" + bucketName + ")");

					S3Object object = s3.getObject(new GetObjectRequest(bucketName, key));

					try {
						Files.copy(object.getObjectContent(), Paths.get("./" + key));
					} catch (IOException ex) {
						ex.printStackTrace();
					}
				}
			}
		});

		delete = new JButton("Delete");
		delete.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (selected_path != null) {
					Object[] element = selected_path.getPath();

					if (element.length == 2) { // Bucket
						String bucketName = element[1].toString();

						int choice = JOptionPane.showOptionDialog(null,
								"Are you sure you would like to delete the bucket (" + bucketName + ") from the cloud?",
								"Delete Bucket?", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, null,
								null);

						if (choice == JOptionPane.YES_OPTION) {
							// Delete the Bucket
							s3.deleteBucket(bucketName);

							// Delete from GUI file manager
							DefaultMutableTreeNode objectNode = (DefaultMutableTreeNode) element[1];
							treeModel.removeNodeFromParent(objectNode);

							// Display delete status
							label.setText("Deleted bucket **" + bucketName + "** successfully!");
						}
					} else if (element.length == 3) { // Object
						String bucketName = element[1].toString();
						String key = element[2].toString();

						int choice = JOptionPane.showOptionDialog(null,
								"Are you sure you would like to delete the object (" + key + ") in bucket (" + bucketName
										+ ") from the cloud?",
								"Delete Bucket?", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, null,
								null);

						if (choice == JOptionPane.YES_OPTION) {
							// Delete from AWS
							s3.deleteObject(bucketName, key);

							// Delete from GUI file manager
							DefaultMutableTreeNode objectNode = (DefaultMutableTreeNode) element[2];
							treeModel.removeNodeFromParent(objectNode);

							// Display delete status
							label.setText("Deleted object **" + key + "** successfully!");
						}
					}

				}

			}
		});

		create_bucket = new JButton("Create Bucket");
		create_bucket.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				String name = JOptionPane.showInputDialog(null, "Enter Bucket Name", "Create Bucket Wizard",
						JOptionPane.QUESTION_MESSAGE, null, null, null).toString();

				if (name != null) {
					try {
						// Create bucket (might throw illegal name exception)
						s3.createBucket(name);

						// create a new node to represent the data and insert it into the model
						DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(name);
						treeModel.insertNodeInto(newNode, root, root.getChildCount());

						// make the new node visible and scroll so that it's visible
						TreePath path = new TreePath(root);
						tree.makeVisible(path.pathByAddingChild(newNode));
						tree.scrollRectToVisible(tree.getPathBounds(path.pathByAddingChild(newNode)));

						// Display uploading status
						label.setText("Created bucket **" + name + "** successfully!");
					} catch (IllegalBucketNameException ex) {
						// If bucket name is invalid, show a message window explaining why
						JOptionPane.showMessageDialog(null, ex.getMessage());
					}
				}
			}

		});

		p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
		wrap = new JPanel();
		// wrap.add(new JLabel("Show drop location:"));
		wrap.add(download);
		wrap.add(delete);
		wrap.add(create_bucket);
		p.add(Box.createHorizontalStrut(4));
		p.add(Box.createGlue());
		p.add(wrap);
		p.add(Box.createGlue());
		p.add(Box.createHorizontalStrut(4));
		getContentPane().add(p, BorderLayout.SOUTH);

		getContentPane().setPreferredSize(new Dimension(400, 450));
	}

	private static void increaseFont(String type) {
		Font font = UIManager.getFont(type);
		font = font.deriveFont(font.getSize() + 4f);
		UIManager.put(type, font);
	}

	private static void createAndShowGUI() {
		// Create and set up the window.
		DragDropFiles test = new DragDropFiles();
		test.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		// Display the window.
		test.pack();
		test.setVisible(true);
	}

	public static void main(String[] args) {
		System.out.println("I have done both of the extra credit options.");
		System.out.println("Buckets can be created, and both buckets and files can be deleted.");
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				try {
					UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
					increaseFont("Tree.font");
					increaseFont("Label.font");
					increaseFont("ComboBox.font");
					increaseFont("List.font");
				} catch (Exception e) {
				}

				// Turn off metal's use of bold fonts
				UIManager.put("swing.boldMetal", Boolean.FALSE);
				createAndShowGUI();
			}
		});
	}
}
