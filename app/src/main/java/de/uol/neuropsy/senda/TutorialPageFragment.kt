package de.uol.neuropsy.senda

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import androidx.fragment.app.Fragment
import coil.ImageLoader
import coil.load


class TutorialPageFragment() : Fragment() {

    companion object {
        // Factory method that creates a new instance of TutorialPageFragment with position as an argument.
        fun newInstance(position: Int): TutorialPageFragment {
            val fragment = TutorialPageFragment()
            val args = Bundle()
            args.putInt("position", position)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Get the position value. Default to 0 if not provided.
        val position = arguments?.getInt("position") ?: 0

        // Choose a layout based on the page position.
        val layoutResId = when (position) {
            0 -> R.layout.tutorial_page_one  // For example, a layout for the first tutorial page.
            1 -> R.layout.tutorial_page_two
            2 -> R.layout.tutorial_page_three
            3 -> R.layout.tutorial_page_four
            4 -> R.layout.tutorial_page_last
            else -> R.layout.tutorial_page_default // A default layout if needed.
        }

        var view = inflater.inflate(layoutResId, container, false)
        view.findViewById<Button>(R.id.close_tutorial_button)?.setOnClickListener({activity?.finish()})
        view.findViewById<Button>(R.id.buttonClose)?.setOnClickListener { activity?.finish() }
        view.findViewById<Button>(R.id.buttonCloseForever)?.setOnClickListener {
            val sharedPref: SharedPreferences =
                requireContext().getSharedPreferences("MyPref", 0)
            val editor = sharedPref.edit()
            editor.putBoolean("firstStart", false)
            editor.commit()
            activity?.finish()
        }
            Log.e("TutorialPageFragment::onCreateView()", "My position: $position")
            // Inflate the selected layout and return it.
        return view
    }
}