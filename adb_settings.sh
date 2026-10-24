# find my_private_details.sh relative to this script
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PRIVATE_DETAILS="$SCRIPT_DIR/my_private_details.sh"
cd "$SCRIPT_DIR"

if [[ ! -r "$PRIVATE_DETAILS" ]]
then
    echo "Missing $PRIVATE_DETAILS"
    echo "Copy my_private_details.sh.example to my_private_details.sh and edit it."
    exit 1
fi

source "$PRIVATE_DETAILS"

adb_phone()
{
    adb -s "$PHONE_SERIAL" "$@"
}

adb_watch()
{
    adb -s "$WATCH_ADDRESS" "$@"
}
