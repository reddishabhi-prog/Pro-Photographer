package com.aiphotographer

import android.app.Application

/**
 * Application entry point.
 *
 * Deliberately empty of wiring: this app has no dependency-injection
 * framework, because the object graph is small enough that constructor
 * arguments are clearer than annotations - and one less annotation processor
 * is one less thing to break a build.
 */
class AIPhotographerApp : Application()
