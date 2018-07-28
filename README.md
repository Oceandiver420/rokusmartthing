# Roku Smartthings SmartApps and Device Handlers

This repo contains various bits of code for controlling Roku devices via smartthings.

# Setup Instructions

0. Go to https://graph.api.smartthings.com/ and setup a developer account
1. Add this repo: MySmartApps -> Settings -> 'Add new repository'
  - Owner: MrStevenFeldman
  - Name: RokuSmartThings
2. Add the following SmartApps:
  -  RokuSmartThings : Roku Manger
3.  Add the following DeviceHandlers:
  - RokuSmartThings : Roku
  - RokuSmartThings : Roku Button Tile


# Using

1. Open the 'Roku Manger' SmartApp
2. Enable the Roku devices that you want.
3. On the next page, add device names for the buttons that you would like to create.

 
# Future Work

- add support for creating push buttons that execute a series of steps.
- rewrite the roku device handler to expand functionality

# Feel free to send me feature requests and/or pull requests


# List of Repos that I used to to build this

+ github.com/MadMouse/SmartThings
  - Initial Roku connect code
+ github.com/kkessler/RokuSmartApp
  - Fixed the directory structure
+ github.com/c99koder/SmartThings
  - Inspired the creation of device handler for each remote button and app.
+ github.com/adamhwang
  - Power on/off fix
