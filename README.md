# SmartGlassesChatGPT

The main purpose of this project is to integrate ChatGPT with your smart glasses.

We can do a ton of things with ChatGpt already and having it right up our faces is a lot more efficient 😉

## Installation

- Install the [SmartGlassesManager](https://github.com/TeamOpenSmartGlasses/SmartGlassesManager) on your phone and make sure it's running
- We need the SmartGlassesManager repo right next to this SmartGlassesChatGpt repo (or you can manually change it in gradle settings)
  - In the future if SmartGlassesManager becomes a package, we might be able to set it up just from gradle
- Build this app in Android Studio and install it on your Android smartphone

## Run the Chatbot

### Initial Setup

1. Open up Android Smart Glasses app on your glasses
2. Open up Smart Glasses Manager on your phone and connect to your glasses
3. Launch the Smart Glasses Chat Gpt app on your phone
4. 2 new commands will appear

### Listening Mode

Activate by saying the phrase "Hey Computer, Listen", allows the app to listen to your conversation nd store them for use for future gpt requests

### Conversation Mode

Activate by saying the phrase "Hey Computer, Conversation", allows you to continuously talk to ChatGpt

### Question Mode

Activate by saying the phrase "Hey Computer, Question", allows you to ask one off questions with ChatGpt

### Difference between Conversation Mode and Question Mode

You get your response in a card format using Question Mode, and will be redirected to the home page once it is done.
In your history, questions asked will persist, it will be recorded as the user has asked a question.

### Example usage flows

- Turn on ```listening mode```, then switch to ```question mode``` whenever you have a question about a previous conversation
- Turn on ```listening mode```, then switch to ```conversation mode``` to talk to gpt about something continuously based on a previous conversation

> You also need to manually switch back to listening mode once you are done with your question or conversation with ChatGpt

## Tech Stack

- Android + Java

## Contributing

If you would like to contribute to this project, please fork the repository and submit a pull request. We welcome contributions of all kinds, including bug fixes, feature requests, and code improvements.

Before submitting a pull request, please make sure that your code adheres to the project's coding standards, and that all tests pass.

### App structure

General guide on how to make a 3rd party app for the Smart Glasses Manager can be found here: [SGM Wiki](https://github.com/TeamOpenSmartGlasses/SmartGlassesManager/wiki)

For our app, its the same, the main thing you might want to look at is the

- ```ChatGptBackend.java``` file for handling the integration logic with the OpenAi Service
- ```ChatGptService.java``` file for handling the sgmLib integration logic

## License

This project is licensed under the MIT License. See the LICENSE file for details.
