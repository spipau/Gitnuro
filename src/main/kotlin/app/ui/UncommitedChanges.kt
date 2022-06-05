@file:OptIn(ExperimentalAnimationApi::class, ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)

package app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerMoveFilter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.extensions.*
import app.git.DiffEntryType
import app.git.StatusEntry
import app.theme.*
import app.ui.components.ScrollableLazyColumn
import app.ui.components.SecondaryButton
import app.ui.context_menu.*
import app.viewmodels.StageStatus
import app.viewmodels.StatusViewModel
import kotlinx.coroutines.flow.collect
import org.eclipse.jgit.lib.RepositoryState

@Composable
fun UncommitedChanges(
    statusViewModel: StatusViewModel,
    selectedEntryType: DiffEntryType?,
    repositoryState: RepositoryState,
    onStagedDiffEntrySelected: (StatusEntry?) -> Unit,
    onUnstagedDiffEntrySelected: (StatusEntry) -> Unit,
    onBlameFile: (String) -> Unit,
    onHistoryFile: (String) -> Unit,
) {
    val stageStatusState = statusViewModel.stageStatus.collectAsState()
    var commitMessage by remember { mutableStateOf(statusViewModel.savedCommitMessage.message) }

    val stageStatus = stageStatusState.value
    val staged: List<StatusEntry>
    val unstaged: List<StatusEntry>

    if (stageStatus is StageStatus.Loaded) {
        staged = stageStatus.staged
        unstaged = stageStatus.unstaged
    } else {
        staged = listOf()
        unstaged = listOf() // return empty lists if still loading
    }

    val doCommit = { amend: Boolean ->
        statusViewModel.commit(commitMessage, amend)
        onStagedDiffEntrySelected(null)
        commitMessage = ""
    }

    val canCommit = commitMessage.isNotEmpty() && staged.isNotEmpty()
    val canAmend = (commitMessage.isNotEmpty() || staged.isNotEmpty()) && statusViewModel.hasPreviousCommits

    LaunchedEffect(Unit) {
        statusViewModel.commitMessageChangesFlow.collect { newCommitMessage ->
            commitMessage = newCommitMessage
        }
    }

    Column {
        AnimatedVisibility(
            visible = stageStatus is StageStatus.Loading,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colors.primaryVariant)
        }

        EntriesList(
            modifier = Modifier
                .padding(start = 8.dp, end = 8.dp, bottom = 4.dp)
                .weight(5f)
                .fillMaxWidth(),
            title = "Staged",
            allActionTitle = "Unstage all",
            actionTitle = "Unstage",
            selectedEntryType = if (selectedEntryType is DiffEntryType.StagedDiff) selectedEntryType else null,
            actionColor = MaterialTheme.colors.unstageButton,
            statusEntries = staged,
            onDiffEntrySelected = onStagedDiffEntrySelected,
            onDiffEntryOptionSelected = {
                statusViewModel.unstage(it)
            },
            onGenerateContextMenu = { statusEntry ->
                statusEntriesContextMenuItems(
                    statusEntry = statusEntry,
                    entryType = EntryType.STAGED,
                    onBlame = { onBlameFile(statusEntry.filePath) },
                    onReset = { statusViewModel.resetStaged(statusEntry) },
                    onHistory = { onHistoryFile(statusEntry.filePath) },
                )
            },
            onAllAction = {
                statusViewModel.unstageAll()
            },
        )

        EntriesList(
            modifier = Modifier
                .padding(start = 8.dp, end = 8.dp, top = 4.dp)
                .weight(5f)
                .fillMaxWidth(),
            title = "Unstaged",
            actionTitle = "Stage",
            selectedEntryType = if (selectedEntryType is DiffEntryType.UnstagedDiff) selectedEntryType else null,
            actionColor = MaterialTheme.colors.stageButton,
            statusEntries = unstaged,
            onDiffEntrySelected = onUnstagedDiffEntrySelected,
            onDiffEntryOptionSelected = {
                statusViewModel.stage(it)
            },
            onGenerateContextMenu = { statusEntry ->
                statusEntriesContextMenuItems(
                    statusEntry = statusEntry,
                    entryType = EntryType.UNSTAGED,
                    onBlame = { onBlameFile(statusEntry.filePath) },
                    onHistory = { onHistoryFile(statusEntry.filePath) },
                    onReset = { statusViewModel.resetUnstaged(statusEntry) },
                    onDelete = {
                        statusViewModel.deleteFile(statusEntry)
                    },
                )
            },
            onAllAction = {
                statusViewModel.stageAll()
            },
            allActionTitle = "Stage all",
        )

        Column(
            modifier = Modifier
                .padding(8.dp)
                .run {
                    // When rebasing, we don't need a fixed size as we don't show the message TextField
                    if (!repositoryState.isRebasing) {
                        height(192.dp)
                    } else
                        this
                }
                .fillMaxWidth()
        ) {
            // Don't show the message TextField when rebasing as it can't be edited
            if (!repositoryState.isRebasing)
                TextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(weight = 1f, fill = true)
                        .onPreviewKeyEvent {
                            if (it.isCtrlPressed && it.key == Key.Enter && canCommit) {
                                doCommit(false)
                                true
                            } else
                                false
                        },
                    value = commitMessage,
                    onValueChange = {
                        commitMessage = it

                        statusViewModel.updateCommitMessage(it)
                    },
                    label = { Text("Write your commit message here", fontSize = 14.sp) },
                    colors = textFieldColors(),
                    textStyle = TextStyle.Default.copy(fontSize = 14.sp, color = MaterialTheme.colors.primaryTextColor),
                )

            when {
                repositoryState.isMerging -> MergeButtons(
                    haveConflictsBeenSolved = unstaged.isEmpty(),
                    onAbort = {
                        statusViewModel.abortMerge()
                        statusViewModel.updateCommitMessage("")
                    },
                    onMerge = { doCommit(false) }
                )
                repositoryState.isRebasing -> RebasingButtons(
                    canContinue = staged.isNotEmpty() || unstaged.isNotEmpty(),
                    haveConflictsBeenSolved = unstaged.isEmpty(),
                    onAbort = {
                        statusViewModel.abortRebase()
                        statusViewModel.updateCommitMessage("")
                    },
                    onContinue = { statusViewModel.continueRebase() },
                    onSkip = { statusViewModel.skipRebase() },
                )
                else -> UncommitedChangesButtons(
                    canCommit = canCommit,
                    canAmend = canAmend,
                    onCommit = { amend -> doCommit(amend) },
                )
            }
        }
    }

}

@Composable
fun UncommitedChangesButtons(
    canCommit: Boolean,
    canAmend: Boolean,
    onCommit: (Boolean) -> Unit
) {
    var showDropDownMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .padding(top = 2.dp)
    ) {
        ConfirmationButton(
            text = "Commit",
            modifier = Modifier
                .weight(1f)
                .height(40.dp),
            onClick = { onCommit(false) },
            enabled = canCommit,
            shape = MaterialTheme.shapes.small.copy(topEnd = CornerSize(0.dp), bottomEnd = CornerSize(0.dp))
        )
        Spacer(
            modifier = Modifier
                .width(1.dp)
                .height(40.dp),
        )

        Box(
            modifier = Modifier
                .height(40.dp)
                .clip(MaterialTheme.shapes.small.copy(topStart = CornerSize(0.dp), bottomStart = CornerSize(0.dp)))
                .background(MaterialTheme.colors.primary)
                .handMouseClickable { showDropDownMenu = true }
        ) {
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .align(Alignment.Center),
            )
            DropdownMenu(
                onDismissRequest = {
                    showDropDownMenu = false
                },
                content = {
                    DropDownContent(
                        enabled = canAmend,
                        dropDownContentData = DropDownContentData(
                            label = "Amend previous commit",
                            icon = null,
                            onClick = { onCommit(true) }
                        ),
                        onDismiss = { showDropDownMenu = false }
                    )
                },
                expanded = showDropDownMenu,
            )
        }
    }
}

@Composable
fun MergeButtons(
    haveConflictsBeenSolved: Boolean,
    onAbort: () -> Unit,
    onMerge: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        AbortButton(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp, end = 4.dp),
            onClick = onAbort
        )

        ConfirmationButton(
            text = "Merge",
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp, end = 4.dp),
            enabled = haveConflictsBeenSolved,
            onClick = onMerge,
        )
    }
}

@Composable
fun RebasingButtons(
    canContinue: Boolean,
    haveConflictsBeenSolved: Boolean,
    onAbort: () -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        AbortButton(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp, end = 4.dp),
            onClick = onAbort
        )

        if (canContinue) {
            ConfirmationButton(
                text = "Continue",
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp, end = 4.dp),
                enabled = haveConflictsBeenSolved,
                onClick = onContinue,
            )
        } else {
            ConfirmationButton(
                text = "Skip",
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp, end = 4.dp),
                onClick = onSkip,
            )
        }

    }
}

@Composable
fun AbortButton(modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = MaterialTheme.colors.abortButton,
            contentColor = Color.White
        )
    ) {
        Text(
            text = "Abort",
            fontSize = 14.sp,
        )
    }
}

@Composable
fun ConfirmationButton(
    text: String,
    modifier: Modifier,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.small,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = MaterialTheme.colors.primary,
            contentColor = Color.White
        )
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
        )
    }
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalFoundationApi::class)
@Composable
private fun EntriesList(
    modifier: Modifier,
    title: String,
    actionTitle: String,
    actionColor: Color,
    statusEntries: List<StatusEntry>,
    onDiffEntrySelected: (StatusEntry) -> Unit,
    onDiffEntryOptionSelected: (StatusEntry) -> Unit,
    onGenerateContextMenu: (StatusEntry) -> List<ContextMenuItem>,
    onAllAction: () -> Unit,
    allActionTitle: String,
    selectedEntryType: DiffEntryType?,
) {
    Column(
        modifier = modifier
    ) {
        Box {
            Text(
                modifier = Modifier
                    .background(color = MaterialTheme.colors.headerBackground)
                    .padding(vertical = 8.dp)
                    .fillMaxWidth(),
                text = title,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colors.headerText,
                fontSize = 13.sp,
                maxLines = 1,
            )

            SecondaryButton(
                modifier = Modifier.align(Alignment.CenterEnd),
                text = allActionTitle,
                backgroundButton = actionColor,
                onClick = onAllAction
            )
        }

        ScrollableLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
        ) {
            itemsIndexed(statusEntries) { index, statusEntry ->
                val isEntrySelected = selectedEntryType != null &&
                        selectedEntryType is DiffEntryType.UncommitedDiff && // Added for smartcast
                        selectedEntryType.statusEntry == statusEntry
                FileEntry(
                    statusEntry = statusEntry,
                    isSelected = isEntrySelected,
                    actionTitle = actionTitle,
                    actionColor = actionColor,
                    onClick = {
                        onDiffEntrySelected(statusEntry)
                    },
                    onButtonClick = {
                        onDiffEntryOptionSelected(statusEntry)
                    },
                    onGenerateContextMenu = onGenerateContextMenu,
                )

                if (index < statusEntries.size - 1) {
                    Divider(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@OptIn(
    ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class,
    ExperimentalAnimationApi::class
)
@Composable
private fun FileEntry(
    statusEntry: StatusEntry,
    isSelected: Boolean,
    actionTitle: String,
    actionColor: Color,
    onClick: () -> Unit,
    onButtonClick: () -> Unit,
    onGenerateContextMenu: (StatusEntry) -> List<ContextMenuItem>,
) {
    var active by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .handMouseClickable { onClick() }
            .fillMaxWidth()
            .pointerMoveFilter(
                onEnter = {
                    active = true
                    false
                },
                onExit = {
                    active = false
                    false
                }
            )
    ) {
        ContextMenuArea(
            items = {
                onGenerateContextMenu(statusEntry)
            },
        ) {
            Row(
                modifier = Modifier
                    .height(40.dp)
                    .fillMaxWidth()
                    .backgroundIf(isSelected, MaterialTheme.colors.backgroundSelected),
                verticalAlignment = Alignment.CenterVertically,
            ) {

                Icon(
                    imageVector = statusEntry.icon,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .size(16.dp),
                    tint = statusEntry.iconColor,
                )

                if (statusEntry.parentDirectoryPath.isNotEmpty()) {
                    Text(
                        text = statusEntry.parentDirectoryPath,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        softWrap = false,
                        fontSize = 13.sp,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colors.secondaryTextColor,
                    )
                }
                Text(
                    text = statusEntry.fileName,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    softWrap = false,
                    fontSize = 13.sp,
                    color = MaterialTheme.colors.primaryTextColor,
                )
            }
        }
        AnimatedVisibility(
            modifier = Modifier
                .align(Alignment.CenterEnd),
            visible = active,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            SecondaryButton(
                onClick = onButtonClick,
                text = actionTitle,
                backgroundButton = actionColor,
                modifier = Modifier
                    .padding(horizontal = 16.dp),
            )
        }
    }
}