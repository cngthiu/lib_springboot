package com.example.books;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/members")
public class MemberController {

    private final MemberService service;

    public MemberController(MemberService service) {
        this.service = service;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "10") int size,
                       @RequestParam(required = false) String q,
                       Model model) {

        Pageable pageable = PageRequest.of(Math.max(page,0), Math.max(size,1));
        Page<MemberDTO> data = service.findAll(q, pageable);

        model.addAttribute("data", data);
        model.addAttribute("members", data.getContent());
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("page", data.getNumber());
        model.addAttribute("size", data.getSize());
        model.addAttribute("totalPages", data.getTotalPages());
        return "members/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("member", new MemberDTO());
        model.addAttribute("mode", "create");
        return "members/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("member") MemberDTO member, BindingResult binding, Model model) {
        if (binding.hasErrors()) {
            model.addAttribute("mode", "create");
            return "members/form";
        }
        service.save(member);
        return "redirect:/members";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("member", service.findById(id));
        model.addAttribute("mode", "edit");
        return "members/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("member") MemberDTO member, BindingResult binding, Model model) {
        if (binding.hasErrors()) {
            model.addAttribute("mode", "edit");
            return "members/form";
        }
        member.setId(id);
        service.save(member);
        return "redirect:/members";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        service.delete(id);
        return "redirect:/members";
    }
}