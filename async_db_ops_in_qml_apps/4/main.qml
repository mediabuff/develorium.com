/*

Copyright Michael Kocherov, 2015
http://develorium.com

*/
import QtQuick 2.2
import QtQuick.Window 2.1

Window {
    visible: true
    width: 800
    height: 600
    signal searchButtonPressed(string theSearchPhrase)
	signal statisticsButtonPressed()

    Rectangle {
		anchors.fill: parent
		color: "#2d333a"

		Image {
			id: logo
			anchors.horizontalCenter: parent.horizontalCenter
			anchors.verticalCenter: parent.verticalCenter
			source: "http://develorium.com/wp-content/uploads/2015/02/logo2_3.png"
		}

		Timer {
			interval: 40
			repeat: true
			running: true
			onTriggered: logo.rotation += 1
		}

		MouseArea {
			anchors.fill: parent
			acceptedButtons: Qt.LeftButton | Qt.RightButton
			onClicked: {
				if(mouse.button == Qt.LeftButton) {
					searchButtonPressed('test')
				}
				else if(mouse.button == Qt.RightButton) {
					statisticsButtonPressed()
				}
			}
		}
    }
}