# RestoreChunk

A plugin used to restore chunks from region files.

## Downloads
[Modrinth](https://modrinth.com/plugin/restorechunk/versions) | [Hangar](https://hangar.papermc.io/Warrior/RestoreChunk/versions) | [Github](https://github.com/Warriorrrr/RestoreChunk/releases)

## Usage
The main command used by the plugin is /restorechunk. To start, you first need to place the
region file you want to restore from in the `/plugins/RestoreChunk/$world$/region` directory.
You will then be able to use the command to restore the current chunk you're standing in.

### Custom arguments
Custom arguments can be used with the restorechunk command to fine tune which blocks get restored.

#### `i:`/`include:`
Limits restored blocks to the given blocks.

Example: `/restorechunk include:dirt,stone` will only restore any missing dirt or stone in the chunk.

#### `p:`/`predicate:`
Allows you to use the blocks x, y, or z coordinate as a factor for whether it gets restored or not.

Example: `/restorechunk p:y>70` will only restore blocks above y level 70.

| Operator | Description           |
|----------|-----------------------|
| \>       | Greater than          |
| <        | Lesser than           |
| \>=      | Greater than or equal |
| <=       | Lesser than or equal  |
| =        | Equals                |
| %        | Remainder (true if 0) |
| &        | Bit mask (true if 0)  |

#### `#preview`
Allows you to preview the changes without altering the world. You can use `/restorechunk apply` to
apply the changes you're previewing. There is currently no way to cancel a preview, other than
walking away until the chunk is out of view distance.

#### `#relight`
Relights all adjacent chunks, useful if there are lightning issues at the chunk edge.

## Permissions
There is currently only the `restorechunk.command.restorechunk` permission, used for the main command
and given to operators by default.
