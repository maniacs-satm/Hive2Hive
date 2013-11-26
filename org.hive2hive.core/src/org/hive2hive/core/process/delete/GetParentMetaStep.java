package org.hive2hive.core.process.delete;

import org.apache.log4j.Logger;
import org.hive2hive.core.H2HConstants;
import org.hive2hive.core.exceptions.GetFailedException;
import org.hive2hive.core.exceptions.PutFailedException;
import org.hive2hive.core.log.H2HLoggerFactory;
import org.hive2hive.core.model.FileTreeNode;
import org.hive2hive.core.model.MetaDocument;
import org.hive2hive.core.model.UserProfile;
import org.hive2hive.core.network.data.UserProfileManager;
import org.hive2hive.core.process.common.get.GetMetaDocumentStep;
import org.hive2hive.core.security.EncryptionUtil;
import org.hive2hive.core.security.EncryptionUtil.RSA_KEYLENGTH;

/**
 * Gets the meta folder of the parent. If the parent is root, there is no need to update it. Else, the deleted
 * document is also removed from the parent meta folder.
 * 
 * @author Nico
 * 
 */
public class GetParentMetaStep extends GetMetaDocumentStep {

	private final static Logger logger = H2HLoggerFactory.getLogger(GetParentMetaStep.class);

	private final MetaDocument metaDocumentToDelete;

	public GetParentMetaStep(MetaDocument metaDocumentToDelete) {
		// TODO this keypair ist just for omitting a NullPointerException at the superclass.
		// There should be a super-constructor not taking any arguments
		super(EncryptionUtil.generateRSAKeyPair(RSA_KEYLENGTH.BIT_512), null, null);
		this.metaDocumentToDelete = metaDocumentToDelete;
	}

	@Override
	public void start() {
		DeleteFileProcessContext context = (DeleteFileProcessContext) getProcess().getContext();
		UserProfileManager profileManager = context.getProfileManager();
		UserProfile userProfile = null;
		try {
			userProfile = profileManager.getUserProfile(getProcess());
		} catch (GetFailedException e1) {
			getProcess().stop(e1.getMessage());
			return;
		}

		// update the profile here because it does not matter whether the parent meta data needs to be updated
		// or not.
		FileTreeNode fileNode = userProfile.getFileById(metaDocumentToDelete.getId());
		if (!fileNode.getChildren().isEmpty()) {
			getProcess().stop("Can only delete empty directory");
			return;
		}

		profileManager.startModification(getProcess());
		FileTreeNode parent = fileNode.getParent();
		parent.removeChild(fileNode);
		try {
			profileManager.putUserProfile(getProcess());
		} catch (PutFailedException e) {
			getProcess().stop(e.getMessage());
			return;
		}

		if (parent.equals(userProfile.getRoot())) {
			// no parent to update since the file is in root
			logger.debug("File is in root; skip getting the parent meta folder and update the profile directly");

			// TODO notify other clients
			getProcess().setNextStep(null);
		} else {
			// normal case when file is not in root
			logger.debug("Get the meta folder of the parent");

			super.nextStep = new UpdateParentMetaStep(metaDocumentToDelete.getId());
			super.keyPair = parent.getKeyPair();
			super.context = context;
			super.get(key2String(parent.getKeyPair().getPublic()), H2HConstants.META_DOCUMENT);
		}
	}

}