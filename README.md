# Roku Smartthings SmartApps and Device Handlers

This repo contains various bits of code for controlling Roku devices via smartthings.

## List of Repos that have been copied into this:

+ github.com/MadMouse/SmartThings
  - Initial Roku connect code
+ github.com/kkessler/RokuSmartApp
  - Fixed the directory structure
+ github.com/c99koder/SmartThings
  - Roku Virtual button magic
+ github.com/adamhwang
  - Power on/off fix


# Setup Instructions

0. Go to https://graph.api.smartthings.com/ and setup a developer account
1. Add this repo: MySmartApps -> Settings -> 'Add new repository'
  - Owner: MrStevenFeldman
  - Name: RokuSmartThings
2. Add the following SmartApps:
  -  RokuSmartThings : Roku (Connect)
  -  RokuSmartThings : Roku Virtual Buttons
3.  Add the following DeviceHandlers:
  - RokuSmartThings : Roku
  - RokuSmartThings : Roku Button Tile
4. Enjoy

# Using

1. Use the 'Roku (Connect)' SmartApp to connect to your rokue
2. Use the 'Roku Virtual Buttons' SmartApp to add pushbutton tiles to your smartthings account

 2.a Select the roku from step 1
 2.b Enable the remote and/or apps that you want a pushbutton
 
# Future Work

- Review the connect code and try to merge it with the virtual buttons app (no need for two apps)
- add support for creating push buttons that execute a series of steps.

# Feel free to send me feature requests and/or pull requests
